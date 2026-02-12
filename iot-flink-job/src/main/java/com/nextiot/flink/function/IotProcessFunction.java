package com.nextiot.flink.function;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.entity.MetricData;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.entity.ThingDevice;
import com.nextiot.common.entity.ConfigChangeEvent;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.util.AviatorUtil;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * IoT 数据处理核心函数
 * 职责：
 * 1. 动态解析（ParseRule + Aviator）
 * 2. 设备合法性校验（防止非法设备污染）
 * 3. 动态规则检测（连续 N 次 / 窗口）
 * 4. 流内抑制（防刷屏）
 * 5. 离线检测
 */
public class IotProcessFunction
        extends KeyedBroadcastProcessFunction<String, String, ConfigChangeEvent, MetricData> {

    private static final Logger log = LoggerFactory.getLogger(IotProcessFunction.class);

    // Side Output 标签：非法设备数据
    public static final OutputTag<String> DIRTY_DATA_TAG = new OutputTag<String>("dirty-data") {};

    // 状态描述符
    private static final ValueStateDescriptor<Integer> CONTINUOUS_COUNT_DESC =
            new ValueStateDescriptor<>("continuous-count", Types.INT);
    private static final ValueStateDescriptor<Long> LAST_ALARM_TIME_DESC =
            new ValueStateDescriptor<>("last-alarm-time", Types.LONG);
    private static final ValueStateDescriptor<LinkedList<Long>> WINDOW_TS_DESC =
            new ValueStateDescriptor<>("window-ts", Types.GENERIC);
    private static final ValueStateDescriptor<Long> LAST_SEEN_DESC =
            new ValueStateDescriptor<>("last-seen", Types.LONG);

    // 广播状态描述符（在 Context 中动态获取）

    // 状态变量
    private transient ValueState<Integer> continuousCountState;
    private transient ValueState<Long> lastAlarmTimeState;
    private transient ValueState<LinkedList<Long>> windowTsState;
    private transient ValueState<Long> lastSeenState;

    @Override
    public void open(Configuration parameters) {
        continuousCountState = getRuntimeContext().getState(CONTINUOUS_COUNT_DESC);
        lastAlarmTimeState = getRuntimeContext().getState(LAST_ALARM_TIME_DESC);
        windowTsState = getRuntimeContext().getState(WINDOW_TS_DESC);
        lastSeenState = getRuntimeContext().getState(LAST_SEEN_DESC);
    }

    /**
     * 处理主流元素（原始报文）
     * 关键步骤：
     * 1. 动态解析报文 → MetricData
     * 2. 设备合法性校验（第一道业务闸门）
     * 3. 告警规则检测
     * 4. 流内抑制
     * 5. 输出已抑制的告警事件
     */
    @Override
    public void processElement(String rawJson, ReadOnlyContext ctx,
                               Collector<MetricData> out) throws Exception {

        try {
            // Step 1: 动态解析
            MetricData metric = parseRawMessage(rawJson, ctx);
            if (metric == null) {
                return; // 解析失败，丢弃
            }

            // Step 2: 设备合法性校验（关键！防止非法设备污染 Flink State）
            if (!validateDevice(metric.getDeviceCode(), ctx)) {
                log.warn("非法设备数据: deviceCode={}", metric.getDeviceCode());
                ctx.output(DIRTY_DATA_TAG, rawJson); // Side Output 到脏数据
                return; // 不进入后续流程
            }

            // Step 3: 检测告警规则
            AlarmEventWrapper alarmEvent = detectAlarm(metric, ctx);
            if (alarmEvent != null) {
                // Step 4: 流内抑制（防刷屏）
                if (!suppressAlarm(alarmEvent, ctx)) {
                    // 发送告警事件到 alarm-event-topic
                    sendAlarmEvent(alarmEvent, ctx);
                }
            }

            // 输出标准化点位数据到 metric-topic（可选）
            out.collect(metric);

            // Step 5: 更新离线检测状态
            updateLastSeen(metric.getDeviceCode(), metric.getTs(), ctx);

        } catch (Exception e) {
            log.error("处理元素失败: rawJson={}", rawJson, e);
        }
    }

    /**
     * 动态解析报文
     * 使用 ParseRule + Aviator Script
     */
    private MetricData parseRawMessage(String rawJson, ReadOnlyContext ctx) {
        try {
            JSONObject raw = JSON.parseObject(rawJson);

            // 从广播状态获取解析规则
            Map<String, ParseRule> parseRules = ctx.getBroadcastState(
                    new org.apache.flink.api.common.state.MapStateDescriptor<>(
                            "parse-rules", Types.STRING, Types.POJO(ParseRule.class)));

            if (parseRules.isEmpty()) {
                log.warn("没有可用的解析规则");
                return null;
            }

            // 查找匹配的解析规则
            ParseRule matchedRule = null;
            Map<String, Object> env = new HashMap<>();
            env.put("raw", raw);

            for (ParseRule rule : parseRules.values()) {
                if (!rule.getEnabled()) {
                    continue;
                }
                // 使用 Aviator 表达式匹配
                if (Boolean.TRUE.equals(AviatorUtil.evalBoolean(rule.getMatchExpr(), env))) {
                    matchedRule = rule;
                    break;
                }
            }

            if (matchedRule == null) {
                log.warn("没有匹配的解析规则: {}", rawJson);
                return null;
            }

            // 执行解析脚本，提取标准字段
            Object parsed = AviatorUtil.eval(matchedRule.getMappingScript(), env);
            if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;
                MetricData metric = new MetricData();
                metric.setDeviceCode(String.valueOf(map.get("deviceCode")));
                metric.setPropertyCode(String.valueOf(map.get("propertyCode")));

                Object value = map.get("value");
                if (value != null) {
                    metric.setValue(((Number) value).doubleValue());
                    metric.setStrValue(String.valueOf(value));
                }

                Object ts = map.get("ts");
                if (ts != null) {
                    metric.setTs(((Number) ts).longValue());
                } else {
                    metric.setTs(System.currentTimeMillis());
                }

                return metric;
            }

        } catch (Exception e) {
            log.error("解析报文失败: {}", rawJson, e);
        }
        return null;
    }

    /**
     * 设备合法性校验
     * 这是 Flink 中数据入口的第一道业务闸门！
     * 不在设备白名单中的数据 → Side Output / 丢弃
     */
    private boolean validateDevice(String deviceCode, ReadOnlyContext ctx) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, ThingDevice> devices = (Map<String, ThingDevice>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "thing-devices", Types.STRING, Types.POJO(ThingDevice.class)));

        return devices.containsKey(deviceCode);
    }

    /**
     * 告警规则检测
     * 支持：连续 N 次 / 时间窗口
     */
    private AlarmEventWrapper detectAlarm(MetricData metric, ReadOnlyContext ctx) throws Exception {
        String key = metric.getDeviceCode() + ":" + metric.getPropertyCode();

        // 从广播状态获取告警规则
        @SuppressWarnings("unchecked")
        Map<String, AlarmRule> alarmRules = (Map<String, AlarmRule>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "alarm-rules", Types.STRING, Types.POJO(AlarmRule.class)));

        if (alarmRules.isEmpty()) {
            return null;
        }

        // 查找匹配的告警规则
        AlarmRule matchedRule = null;
        Map<String, Object> env = new HashMap<>();
        env.put("value", metric.getValue());
        env.put("deviceCode", metric.getDeviceCode());
        env.put("propertyCode", metric.getPropertyCode());

        for (AlarmRule rule : alarmRules.values()) {
            if (!rule.getEnabled()) {
                continue;
            }
            // 检查设备匹配
            if (rule.getDeviceCode() != null && !rule.getDeviceCode().equals(metric.getDeviceCode())) {
                continue;
            }
            if (!rule.getPropertyCode().equals(metric.getPropertyCode())) {
                continue;
            }
            // 使用 Aviator 表达式判断条件
            if (Boolean.TRUE.equals(AviatorUtil.evalBoolean(rule.getConditionExpr(), env))) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule == null) {
            return null;
        }

        // 根据触发类型进行检测
        boolean triggered = false;

        if (matchedRule.getTriggerType() == TriggerType.CONTINUOUS_N) {
            // 连续 N 次检测
            Integer count = continuousCountState.value();
            if (count == null) {
                count = 0;
            }
            count++;
            continuousCountState.update(count);

            if (count >= matchedRule.getTriggerN()) {
                triggered = true;
                continuousCountState.clear(); // 清空计数
            }

        } else if (matchedRule.getTriggerType() == TriggerType.WINDOW) {
            // 时间窗口检测
            Long now = metric.getTs();
            Integer windowSize = matchedRule.getWindowSeconds() * 1000;

            LinkedList<Long> timestamps = windowTsState.value();
            if (timestamps == null) {
                timestamps = new LinkedList<>();
            }

            // 清理过期时间戳
            timestamps.removeIf(ts -> now - ts > windowSize);
            timestamps.add(now);

            if (timestamps.size() >= matchedRule.getTriggerN()) {
                triggered = true;
                timestamps.clear();
            }

            windowTsState.update(timestamps);
        }

        if (triggered) {
            AlarmEventWrapper event = new AlarmEventWrapper();
            event.setRuleCode(matchedRule.getRuleCode());
            event.setDeviceCode(metric.getDeviceCode());
            event.setPropertyCode(metric.getPropertyCode());
            event.setValue(metric.getValue());
            event.setLevel(matchedRule.getLevel());
            event.setDescription(matchedRule.getDescription());
            event.setTs(metric.getTs());
            event.setSuppressSeconds(matchedRule.getSuppressSeconds());
            return event;
        }

        return null;
    }

    /**
     * 流内抑制
     * 防止同一规则 + 同一设备在短时间内重复告警
     */
    private boolean suppressAlarm(AlarmEventWrapper alarmEvent, ReadOnlyContext ctx) throws Exception {
        String suppressKey = alarmEvent.getRuleCode() + ":" + alarmEvent.getDeviceCode();
        Long lastAlarmTime = lastAlarmTimeState.value();

        if (lastAlarmTime != null) {
            long elapsed = System.currentTimeMillis() - lastAlarmTime;
            if (elapsed < alarmEvent.getSuppressSeconds() * 1000) {
                log.debug("告警被流内抑制: ruleCode={}, deviceCode={}, elapsed={}ms",
                        alarmEvent.getRuleCode(), alarmEvent.getDeviceCode(), elapsed);
                return true;
            }
        }

        // 更新最后告警时间
        lastAlarmTimeState.update(System.currentTimeMillis());
        return false;
    }

    /**
     * 发送告警事件
     */
    private void sendAlarmEvent(AlarmEventWrapper alarmEvent, ReadOnlyContext ctx) {
        String json = JSON.toJSONString(alarmEvent);
        // 使用 Kafka Sink 发送（实际实现需要配置 Kafka Producer）
        log.info("发送告警事件: ruleCode={}, deviceCode={}, value={}",
                alarmEvent.getRuleCode(), alarmEvent.getDeviceCode(), alarmEvent.getValue());
        // TODO: 实际发送到 Kafka alarm-event-topic
    }

    /**
     * 更新最后上报时间（用于离线检测）
     */
    private void updateLastSeen(String deviceCode, Long ts, ReadOnlyContext ctx) throws Exception {
        lastSeenState.update(ts);

        // 注册定时器进行离线检测
        @SuppressWarnings("unchecked")
        Map<String, OfflineRule> offlineRules = (Map<String, OfflineRule>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "offline-rules", Types.STRING, Types.POJO(OfflineRule.class)));

        OfflineRule rule = offlineRules.get(deviceCode);
        if (rule != null && rule.getEnabled()) {
            long timeoutMs = rule.getTimeoutSeconds() * 1000L;
            ctx.timerService().registerProcessingTimeTimer(ts + timeoutMs);
        }
    }

    /**
     * 处理广播元素（配置变更事件）
     * 动态刷新 Broadcast State
     */
    @Override
    public void processBroadcastElement(ConfigChangeEvent event, Context ctx,
                                        Collector<MetricData> out) throws Exception {

        log.info("收到配置变更事件: type={}, op={}", event.getType(), event.getOp());

        ConfigChangeType type = event.getType();
        ConfigOpType op = event.getOp();
        Map<String, Object> payload = event.getPayload();

        switch (type) {
            case DEVICE:
                updateDeviceState(payload, op, ctx);
                break;
            case PARSE_RULE:
                updateParseRuleState(payload, op, ctx);
                break;
            case ALARM_RULE:
                updateAlarmRuleState(payload, op, ctx);
                break;
            case OFFLINE_RULE:
                updateOfflineRuleState(payload, op, ctx);
                break;
        }
    }

    /**
     * 更新设备广播状态
     */
    private void updateDeviceState(Map<String, Object> payload, ConfigOpType op, Context ctx) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, ThingDevice> deviceState = (Map<String, ThingDevice>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "thing-devices", Types.STRING, Types.POJO(ThingDevice.class)));

        String deviceCode = (String) payload.get("deviceCode");
        ThingDevice device = JSON.parseObject(JSON.toJSONString(payload), ThingDevice.class);

        switch (op) {
            case ADD:
            case UPDATE:
                deviceState.put(deviceCode, device);
                log.info("设备已更新: {}", deviceCode);
                break;
            case DELETE:
                deviceState.remove(deviceCode);
                log.info("设备已删除: {}", deviceCode);
                break;
        }
    }

    /**
     * 更新解析规则广播状态
     */
    private void updateParseRuleState(Map<String, Object> payload, ConfigOpType op, Context ctx) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, ParseRule> ruleState = (Map<String, ParseRule>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "parse-rules", Types.STRING, Types.POJO(ParseRule.class)));

        String gatewayType = (String) payload.get("gatewayType");
        Integer version = (Integer) payload.get("version");
        String ruleKey = gatewayType + ":" + version;

        ParseRule rule = JSON.parseObject(JSON.toJSONString(payload), ParseRule.class);

        switch (op) {
            case ADD:
            case UPDATE:
                ruleState.put(ruleKey, rule);
                log.info("解析规则已更新: {}", ruleKey);
                break;
            case DELETE:
                ruleState.remove(ruleKey);
                log.info("解析规则已删除: {}", ruleKey);
                break;
        }
    }

    /**
     * 更新告警规则广播状态
     */
    private void updateAlarmRuleState(Map<String, Object> payload, ConfigOpType op, Context ctx) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, AlarmRule> ruleState = (Map<String, AlarmRule>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "alarm-rules", Types.STRING, Types.POJO(AlarmRule.class)));

        String ruleCode = (String) payload.get("ruleCode");
        AlarmRule rule = JSON.parseObject(JSON.toJSONString(payload), AlarmRule.class);

        switch (op) {
            case ADD:
            case UPDATE:
                ruleState.put(ruleCode, rule);
                log.info("告警规则已更新: {}", ruleCode);
                break;
            case DELETE:
                ruleState.remove(ruleCode);
                log.info("告警规则已删除: {}", ruleCode);
                break;
        }
    }

    /**
     * 更新离线规则广播状态
     */
    private void updateOfflineRuleState(Map<String, Object> payload, ConfigOpType op, Context ctx) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, OfflineRule> ruleState = (Map<String, OfflineRule>) ctx.getBroadcastState(
                new org.apache.flink.api.common.state.MapStateDescriptor<>(
                        "offline-rules", Types.STRING, Types.POJO(OfflineRule.class)));

        String deviceCode = (String) payload.get("deviceCode");
        OfflineRule rule = JSON.parseObject(JSON.toJSONString(payload), OfflineRule.class);

        switch (op) {
            case ADD:
            case UPDATE:
                ruleState.put(deviceCode, rule);
                log.info("离线规则已更新: {}", deviceCode);
                break;
            case DELETE:
                ruleState.remove(deviceCode);
                log.info("离线规则已删除: {}", deviceCode);
                break;
        }
    }

    /**
     * 定时器触发 - 离线检测
     */
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<MetricData> out) throws Exception {
        Long lastSeen = lastSeenState.value();

        if (lastSeen == null || lastSeen + 10000 < timestamp) { // 10秒容错
            // 设备离线，发送离线告警
            String deviceCode = ctx.getCurrentKey();
            log.warn("设备离线: deviceCode={}", deviceCode);

            AlarmEventWrapper offlineEvent = new AlarmEventWrapper();
            offlineEvent.setRuleCode("OFFLINE_" + deviceCode);
            offlineEvent.setDeviceCode(deviceCode);
            offlineEvent.setDescription("设备离线");
            offlineEvent.setTs(timestamp);

            sendAlarmEvent(offlineEvent, ctx);
        }
    }

    /**
     * 告警事件包装类（用于内部传递）
     */
    public static class AlarmEventWrapper {
        private String ruleCode;
        private String deviceCode;
        private String propertyCode;
        private Double value;
        private String strValue;
        private Integer level;
        private String description;
        private Long ts;
        private Integer suppressSeconds;

        // Getters and Setters
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getDeviceCode() { return deviceCode; }
        public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
        public String getPropertyCode() { return propertyCode; }
        public void setPropertyCode(String propertyCode) { this.propertyCode = propertyCode; }
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
        public String getStrValue() { return strValue; }
        public void setStrValue(String strValue) { this.strValue = strValue; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getTs() { return ts; }
        public void setTs(Long ts) { this.ts = ts; }
        public Integer getSuppressSeconds() { return suppressSeconds; }
        public void setSuppressSeconds(Integer suppressSeconds) { this.suppressSeconds = suppressSeconds; }
    }
}