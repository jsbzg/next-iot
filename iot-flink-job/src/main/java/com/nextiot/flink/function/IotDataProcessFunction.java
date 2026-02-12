package com.nextiot.flink.function;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nextiot.common.entity.*;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.util.AviatorUtil;
import com.nextiot.flink.state.BroadcastStateKeys;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * IoT 数据中台核心处理函数
 *
 * 功能：
 * 1. 动态解析（ParseRule + Aviator）
 * 2. 设备合法性校验（ThingDevice 广播状态）- 不在库 → Side Output
 * 3. 动态检测规则（AlarmRule + Aviator）- 连续N次 / 窗口
 * 4. 流内抑制（State 防刷屏/窗口去重）
 * 5. 离线检测（OfflineRule + 定时器）
 */
public class IotDataProcessFunction extends KeyedBroadcastProcessFunction<
        String,                                                                 // Key
        Map<String, Object>,                                                  // 主流：原始报文
        ConfigChangeEvent,                                                     // 广播流：配置变更事件
        Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> {                 // 输出：(Metrics, AlarmEvent, OfflineEvent, DeviceCode)

    private static final Logger log = LoggerFactory.getLogger(IotDataProcessFunction.class);

    // 非法设备数据侧输出
    public static final OutputTag<String> DIRTY_DATA_OUTPUT = new OutputTag<String>("dirty-data") {};

    // 可替换点：当前使用 ValueState，后续可使用 Redis 实现分布式状态

    // 连续触发次数状态
    private ValueState<Integer> continuousCountState;

    // 触发历史记录状态（用于窗口判断）
    private ValueState<Tuple2<Long, Double>> triggerHistoryState;

    // 流内抑制状态
    private ValueState<Long> suppressState;

    // 设备最后上报时间状态
    private ValueState<Long> lastSeenTimeState;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        // 初始化连续触发次数状态
        ValueStateDescriptor<Integer> continuousDesc = new ValueStateDescriptor<>(
                "continuousCount", TypeInformation.of(Integer.class));
        continuousCountState = getRuntimeContext().getState(continuousDesc);

        // 初始化触发历史记录状态
        ValueStateDescriptor<Tuple2<Long, Double>> historyDesc = new ValueStateDescriptor<>(
                "triggerHistory", TypeInformation.of(Tuple2.class));
        triggerHistoryState = getRuntimeContext().getState(historyDesc);

        // 初始化流内抑制状态
        ValueStateDescriptor<Long> suppressDesc = new ValueStateDescriptor<>(
                "suppressTime", TypeInformation.of(Long.class));
        suppressState = getRuntimeContext().getState(suppressDesc);

        // 初始化设备最后上报时间状态
        ValueStateDescriptor<Long> lastSeenDesc = new ValueStateDescriptor<>(
                "lastSeenTime", TypeInformation.of(Long.class));
        lastSeenTimeState = getRuntimeContext().getState(lastSeenDesc);
    }

    /**
     * 处理主流：原始报文
     */
    @Override
    public void processElement(
            Map<String, Object> raw,
            ReadOnlyContext ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        // 当前时间戳
        long now = System.currentTimeMillis();
        String deviceCode = String.valueOf(raw.get("deviceCode"));

        // ===== Step 1: 动态解析 =====
        // 获取解析规则（从 Broadcast State）
        ParseRule parseRule = BroadcastStateKeys.getParseRule(ctx.getBroadcastState(parseRuleDesc), "MQTT");
        if (parseRule == null) {
            // 可扩展：支持多种网关类型
            log.warn("未找到解析规则: deviceCode={}", deviceCode);
            return;
        }

        // 解析为标准 MetricData 格式
        MetricData metricData = parseRawData(raw, parseRule);
        if (metricData == null) {
            // 解析失败，Side Output
            ctx.output(DIRTY_DATA_OUTPUT, "解析失败: " + JSON.toJSONString(raw));
            return;
        }

        // ===== Step 2: 设备合法性校验（第一道业务闸门）=====
        // 这是 Flink 中的**第一道业务闸门**
        ThingDevice device = BroadcastStateKeys.getDevice(ctx.getBroadcastState(deviceDesc), deviceCode);
        if (device == null) {
            // 非法设备 / 未注册 / 已删除
            String dirtyMsg = String.format("非法设备数据: deviceCode=%s, raw=%s",
                    deviceCode, JSON.toJSONString(raw));
            log.warn(dirtyMsg);
            ctx.output(DIRTY_DATA_OUTPUT, dirtyMsg);
            // 严禁进入后续流程
            return;
        }

        // 更新设备最后上报时间
        lastSeenTimeState.update(now);

        // ===== Step 3: 设备离线检测设置 =====
        // 获取离线规则
        OfflineRule offlineRule = BroadcastStateKeys.getOfflineRule(ctx.getBroadcastState(offlineRuleDesc), deviceCode);
        if (offlineRule != null && offlineRule.getEnabled()) {
            // 注册定时器检测离线
            long offlineTriggerTime = now + offlineRule.getTimeoutSeconds() * 1000L;
            ctx.timerService().registerProcessingTimeTimer(offlineTriggerTime);
        }

        // ===== Step 4: 告警规则检测 =====
        // 获取匹配该设备的告警规则
        Collection<AlarmRule> rules = getMatchAlarmRules(ctx.getBroadcastState(alarmRuleDesc), deviceCode, metricData.getPropertyCode());

        AlarmEvent alarmEvent = null;
        for (AlarmRule rule : rules) {
            if (!rule.getEnabled()) {
                continue;
            }

            // 条件判断
            Map<String, Object> env = new HashMap<>();
            env.put("value", metricData.getValue());
            boolean matched = AviatorUtil.evalBoolean(rule.getConditionExpr(), env);

            if (matched) {
                // ===== Step 4.1: 触发类型判断（连续N次 / 窗口）=====
                boolean shouldTrigger = false;

                if (rule.getTriggerType() == TriggerType.CONTINUOUS_N) {
                    // 连续 N 次判断
                    Integer count = continuousCountState.value();
                    if (count == null) {
                        count = 0;
                    }
                    count++;
                    continuousCountState.update(count);

                    if (count >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        continuousCountState.clear(); // 触发后清空
                    }
                } else if (rule.getTriggerType() == TriggerType.WINDOW) {
                    // 窗口判断：统计窗口内的触发次数
                    Tuple2<Long, Double> history = triggerHistoryState.value();
                    List<Tuple2<Long, Double>> triggerTimes = new ArrayList<>();
                    if (history != null && (now - history.f0 <= rule.getWindowSeconds() * 1000L)) {
                        triggerTimes.add(history);
                    }

                    triggerTimes.add(Tuple2.of(now, metricData.getValue()));

                    if (triggerTimes.size() >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        triggerTimes.clear();
                    }

                    // 保存最后一条记录
                    if (!triggerTimes.isEmpty()) {
                        triggerHistoryState.update(triggerTimes.get(triggerTimes.size() - 1));
                    } else {
                        triggerHistoryState.clear();
                    }
                }

                // ===== Step 4.2: 流内抑制（防止刷屏）=====
                if (shouldTrigger) {
                    String suppressKey = deviceCode + ":" + rule.getRuleCode();
                    Long lastTriggerTime = suppressState.value();

                    if (lastTriggerTime != null && now - lastTriggerTime < rule.getSuppressSeconds() * 1000L) {
                        // 在抑制时间内，不输出
                        log.debug("告警被抑制: ruleCode={}, deviceCode={}", rule.getRuleCode(), deviceCode);
                        continue;
                    }

                    // 更新抑制时间
                    suppressState.update(now);

                    // 构造告警事件
                    alarmEvent = buildAlarmEvent(rule, deviceCode, metricData, now);
                }
            }
        }

        // 输出结果：(MetricData, AlarmEvent, OfflineEvent, DeviceCode)
        out.collect(Tuple4.of(metricData, alarmEvent, null, deviceCode));
    }

    /**
     * 处理广播流：配置变更事件
     * 实现**无需重启 Flink Job** 即可动态刷新规则
     */
    @Override
    public void processBroadcastElement(
            ConfigChangeEvent event,
            Context ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        log.info("接收到配置变更事件: type={}, op={}", event.getType(), event.getOp());

        switch (event.getType()) {
            case DEVICE:
                handleDeviceChange(event, ctx);
                break;
            case PARSE_RULE:
                handleParseRuleChange(event, ctx);
                break;
            case ALARM_RULE:
                handleAlarmRuleChange(event, ctx);
                break;
            case OFFLINE_RULE:
                handleOfflineRuleChange(event, ctx);
                break;
            default:
                log.warn("未知的配置变更类型: {}", event.getType());
        }
    }

    /**
     * 处理定时器：设备离线检测
     */
    @Override
    public void onTimer(long timestamp,
            OnTimerContext ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        String deviceCode = ctx.getCurrentKey();
        Long lastSeen = lastSeenTimeState.value();
        long now = System.currentTimeMillis();

        // 获取离线规则
        OfflineRule offlineRule = BroadcastStateKeys.getOfflineRule(ctx.getBroadcastState(offlineRuleDesc), deviceCode);
        if (offlineRule != null && offlineRule.getEnabled() && lastSeen != null) {
            // 判断是否超时
            if (now - lastSeen >= offlineRule.getTimeoutSeconds() * 1000L) {
                // 触发离线告警
                AlarmEvent offlineEvent = AlarmEvent.createOfflineEvent(offlineRule, deviceCode, now);
                out.collect(Tuple4.of(null, null, offlineEvent, deviceCode));
                log.info("设备离线告警: deviceCode={}, offlineSeconds={}", deviceCode, offlineRule.getTimeoutSeconds());
            }
        }
    }

    /**
     * 订阅的 Broadcast State 描述符
     */
    public static final ValueStateDescriptor<ThingDevice> deviceDesc =
            new ValueStateDescriptor<>("device-state", ThingDevice.class);

    public static final ValueStateDescriptor<ParseRule> parseRuleDesc =
            new ValueStateDescriptor<>("parse-rule-state", ParseRule.class);

    public static final ValueStateDescriptor<AlarmRule> alarmRuleDesc =
            new ValueStateDescriptor<>("alarm-rule-state", AlarmRule.class);

    public static final ValueStateDescriptor<OfflineRule> offlineRuleDesc =
            new ValueStateDescriptor<>("offline-rule-state", OfflineRule.class);

    // ========== 私有辅助方法 ==========

    /**
     * 解析原始报文为标准格式
     */
    private MetricData parseRawData(Map<String, Object> raw, ParseRule parseRule) {
        try {
            // Demo 简化：假设原始报文已经是标准格式
            Object deviceCodeObj = raw.get("deviceCode");
            if (deviceCodeObj == null) {
                return null;
            }
            String deviceCode = String.valueOf(deviceCodeObj);

            // 提取点位数据 (temperature / humidity / pressure)
            MetricData metric = new MetricData();
            metric.setDeviceCode(deviceCode);

            // 简化的提取逻辑，实际应使用 parseRule 的 mappingScript
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("temperature".equals(key) || "humidity".equals(key) || "pressure".equals(key)) {
                    metric.setPropertyCode(key);
                    if (value instanceof Number) {
                        metric.setValue(((Number) value).doubleValue());
                    }
                    metric.setStrValue(String.valueOf(value));
                    break;
                }
            }

            Object tsObj = raw.get("ts");
            metric.setTs(tsObj != null ? Long.parseLong(String.valueOf(tsObj)) : System.currentTimeMillis());

            return metric;
        } catch (Exception e) {
            log.error("解析报文失败: {}", raw, e);
            return null;
        }
    }

    /**
     * 获取匹配设备的告警规则
     */
    private Collection<AlarmRule> getMatchAlarmRules(
            ReadOnlyBroadcastState<String, AlarmRule> state,
            String deviceCode,
            String propertyCode) throws Exception {

        List<AlarmRule> matchedRules = new ArrayList<>();

        // 遍历所有告警规则，筛选出符合条件的规则
        for (String key : state.immutableEntries().keySet()) {
            AlarmRule rule = state.get(key);
            if (rule != null && rule.getEnabled()) {
                // 规则匹配逻辑：
                // 1. 点位编码匹配
                // 2. 设备编码匹配或规则未指定设备（按模型规则）
                if (propertyCode.equals(rule.getPropertyCode()) &&
                    (deviceCode.equals(rule.getDeviceCode()) || rule.getDeviceCode() == null || rule.getDeviceCode().isEmpty())) {
                    matchedRules.add(rule);
                }
            }
        }

        return matchedRules;
    }

    /**
     * 构造告警事件
     */
    private AlarmEvent buildAlarmEvent(AlarmRule rule, String deviceCode, MetricData metric, long ts) {
        AlarmEvent event = new AlarmEvent();
        event.setRuleCode(rule.getRuleCode());
        event.setDeviceCode(deviceCode);
        event.setPropertyCode(metric.getPropertyCode());
        event.setValue(metric.getValue());
        event.setStrValue(metric.getStrValue());
        event.setLevel(rule.getLevel());
        event.setDescription(rule.getDescription());
        event.setTs(ts);
        return event;
    }

    /**
     * 处理设备变更
     */
    private void handleDeviceChange(ConfigChangeEvent event, Context ctx) throws Exception {
        ThingDevice device = JSON.parseObject(JSON.toJSONString(event.getPayload()), ThingDevice.class);
        if (event.getOp() == ConfigOpType.DELETE) {
            BroadcastStateKeys.deleteDevice(ctx.getBroadcastState(deviceDesc), device.getDeviceCode());
        } else {
            BroadcastStateKeys.updateDevice(ctx.getBroadcastState(deviceDesc), device);
        }
    }

    /**
     * 处理解析规则变更
     */
    private void handleParseRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        ParseRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), ParseRule.class);
        BroadcastStateKeys.updateParseRule(ctx.getBroadcastState(parseRuleDesc), rule);
    }

    /**
     * 处理告警规则变更
     */
    private void handleAlarmRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        AlarmRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), AlarmRule.class);
        if (event.getOp() == ConfigOpType.DELETE) {
            BroadcastStateKeys.deleteAlarmRule(ctx.getBroadcastState(alarmRuleDesc), rule.getRuleCode());
        } else {
            BroadcastStateKeys.updateAlarmRule(ctx.getBroadcastState(alarmRuleDesc), rule);
        }
    }

    /**
     * 处理离线规则变更
     */
    private void handleOfflineRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        OfflineRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), OfflineRule.class);
        if (event.getOp() == ConfigOpType.DELETE) {
            BroadcastStateKeys.deleteOfflineRule(ctx.getBroadcastState(offlineRuleDesc), rule.getDeviceCode());
        } else {
            BroadcastStateKeys.updateOfflineRule(ctx.getBroadcastState(offlineRuleDesc), rule);
        }
    }
}