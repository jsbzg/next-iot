package com.nextiot.flink.function;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.*;
import com.nextiot.common.enums.AlarmLevel;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.util.AviatorUtil;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
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

    // ========== Broadcast State 描述符（MapStateDescriptor）==========
    // 设备状态：key=deviceCode, value=ThingDevice
    public static final MapStateDescriptor<String, ThingDevice> DEVICE_STATE_DESC =
            new MapStateDescriptor<>("device-state", Types.STRING, TypeInformation.of(ThingDevice.class));

    // 解析规则状态：key=gatewayType:version, value=ParseRule
    public static final MapStateDescriptor<String, ParseRule> PARSE_RULE_STATE_DESC =
            new MapStateDescriptor<>("parse-rule-state", Types.STRING, TypeInformation.of(ParseRule.class));

    // 告警规则状态：key=ruleCode, value=AlarmRule
    public static final MapStateDescriptor<String, AlarmRule> ALARM_RULE_STATE_DESC =
            new MapStateDescriptor<>("alarm-rule-state", Types.STRING, TypeInformation.of(AlarmRule.class));

    // 离线规则状态：key=deviceCode, value=OfflineRule
    public static final MapStateDescriptor<String, OfflineRule> OFFLINE_RULE_STATE_DESC =
            new MapStateDescriptor<>("offline-rule-state", Types.STRING, TypeInformation.of(OfflineRule.class));

    // ========== Keyed State ==========
    // 连续触发次数状态
    private ValueState<Integer> continuousCountState;

    // 窗口内触发时间列表状态
    private ListState<Long> windowTriggerTimesState;

    // 流内抑制状态（最后一次告警触发时间）
    private ValueState<Long> suppressState;

    // 设备最后上报时间状态
    private ValueState<Long> lastSeenTimeState;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        continuousCountState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("continuousCount", Types.INT));

        windowTriggerTimesState = getRuntimeContext().getListState(
                new ListStateDescriptor<>("windowTriggerTimes", Types.LONG));

        suppressState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("suppressTime", Types.LONG));

        lastSeenTimeState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("lastSeenTime", Types.LONG));
    }

    /**
     * 处理主流：原始报文
     */
    @Override
    public void processElement(
            Map<String, Object> raw,
            ReadOnlyContext ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        long now = System.currentTimeMillis();
        String deviceCode = String.valueOf(raw.get("deviceCode"));
        log.info("[PARSER-DEBUG] >>> 收到原始数据: deviceCode={}, raw={}", deviceCode, JSON.toJSONString(raw));

        // ===== Step 1: 动态解析 =====
        String gatewayType = raw.get("gatewayType") != null ? String.valueOf(raw.get("gatewayType")) : "MQTT";
        ParseRule parseRule = findLatestParseRule(ctx, gatewayType);
        if (parseRule == null) {
            log.warn("[PARSER-DEBUG] !!! [Step 1] 未找到解析规则: gatewayType={}", gatewayType);
            return;
        }

        MetricData metricData = parseRawData(raw, parseRule);
        if (metricData == null) {
            log.warn("[PARSER-DEBUG] !!! [Step 1] 数据解析失败或被 matchExpr 过滤: raw={}", JSON.toJSONString(raw));
            ctx.output(DIRTY_DATA_OUTPUT, "解析失败: " + JSON.toJSONString(raw));
            return;
        }
        log.info("[PARSER-DEBUG] >>> [Step 1] 解析成功: metric={}", JSON.toJSONString(metricData));

        // ===== Step 2: 设备合法性校验（第一道业务闸门）=====
        ThingDevice device = ctx.getBroadcastState(DEVICE_STATE_DESC).get(deviceCode);
        if (device == null) {
            log.warn("[PARSER-DEBUG] !!! [Step 2] 非法设备(未注册): deviceCode={}", deviceCode);
            String dirtyMsg = String.format("非法设备数据: deviceCode=%s, raw=%s",
                    deviceCode, JSON.toJSONString(raw));
            ctx.output(DIRTY_DATA_OUTPUT, dirtyMsg);
            return;
        }
        log.info("[PARSER-DEBUG] >>> [Step 2] 设备校验通过: {}", deviceCode);

        // 更新设备最后上报时间
        lastSeenTimeState.update(now);

        // ===== Step 3: 设备离线检测设置 =====
        OfflineRule offlineRule = ctx.getBroadcastState(OFFLINE_RULE_STATE_DESC).get(deviceCode);
        if (offlineRule != null && offlineRule.getEnabled()) {
            long offlineTriggerTime = now + offlineRule.getTimeoutSeconds() * 1000L;
            ctx.timerService().registerProcessingTimeTimer(offlineTriggerTime);
        }

        // ===== Step 4: 告警规则检测 =====
        Collection<AlarmRule> rules = getMatchAlarmRules(ctx, deviceCode, metricData.getPropertyCode());

        AlarmEvent alarmEvent = null;
        for (AlarmRule rule : rules) {
            if (!rule.getEnabled()) {
                continue;
            }

            // 条件判断（Aviator 表达式）
            Map<String, Object> env = new HashMap<>();
            env.put("value", metricData.getValue());
            boolean matched = AviatorUtil.evalBoolean(rule.getConditionExpr(), env);

            if (matched) {
                // ===== Step 4.1: 触发类型判断 =====
                boolean shouldTrigger = false;
                String triggerType = rule.getTriggerType();

                if (TriggerType.CONTINUOUS_N.getCode().equals(triggerType)) {
                    // 连续 N 次判断
                    Integer count = continuousCountState.value();
                    if (count == null) {
                        count = 0;
                    }
                    count++;
                    continuousCountState.update(count);

                    if (count >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        continuousCountState.clear();
                    }
                } else if (TriggerType.WINDOW.getCode().equals(triggerType)) {
                    // 窗口判断：统计窗口内的触发次数
                    long windowMs = rule.getWindowSeconds() * 1000L;
                    List<Long> validTimes = new ArrayList<>();

                    for (Long ts : windowTriggerTimesState.get()) {
                        if (now - ts <= windowMs) {
                            validTimes.add(ts);
                        }
                    }
                    validTimes.add(now);

                    if (validTimes.size() >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        validTimes.clear();
                    }

                    // 更新状态
                    windowTriggerTimesState.clear();
                    for (Long ts : validTimes) {
                        windowTriggerTimesState.add(ts);
                    }
                }

                // ===== Step 4.2: 流内抑制（防止刷屏）=====
                if (shouldTrigger) {
                    Long lastTriggerTime = suppressState.value();

                    if (lastTriggerTime != null && now - lastTriggerTime < rule.getSuppressSeconds() * 1000L) {
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

        OfflineRule offlineRule = ctx.getBroadcastState(OFFLINE_RULE_STATE_DESC).get(deviceCode);
        if (offlineRule != null && offlineRule.getEnabled() && lastSeen != null) {
            if (now - lastSeen >= offlineRule.getTimeoutSeconds() * 1000L) {
                AlarmEvent offlineEvent = AlarmEvent.createOfflineEvent(offlineRule, deviceCode, now);
                out.collect(Tuple4.of(null, null, offlineEvent, deviceCode));
                log.info("设备离线告警: deviceCode={}, offlineSeconds={}", deviceCode, offlineRule.getTimeoutSeconds());
            }
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 查找最新版本的解析规则
     */
    private ParseRule findLatestParseRule(ReadOnlyContext ctx, String gatewayType) throws Exception {
        String keyPrefix = gatewayType + ":";
        int maxVersion = 0;
        ParseRule latest = null;

        for (Map.Entry<String, ParseRule> entry : ctx.getBroadcastState(PARSE_RULE_STATE_DESC).immutableEntries()) {
            String key = entry.getKey();
            if (key.startsWith(keyPrefix)) {
                String versionStr = key.substring(keyPrefix.length());
                try {
                    int version = Integer.parseInt(versionStr);
                    if (version > maxVersion) {
                        maxVersion = version;
                        latest = entry.getValue();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return latest;
    }

    /**
     * 解析原始报文为标准格式
     */
    private MetricData parseRawData(Map<String, Object> raw, ParseRule parseRule) {
        try {
            // 1. 动态脚本模式 (优先)
            if (parseRule.getParseScript() != null && !parseRule.getParseScript().isBlank()) {
                log.info("[PARSER-DEBUG] ---------------------------------------------");
                log.info("[PARSER-DEBUG] 规则检测开始: 规则ID={}, GatewayType={}, Version={}", 
                        parseRule.getId(), parseRule.getGatewayType(), parseRule.getVersion());

                // 1.1 匹配表达式校验
                Map<String, Object> env = new HashMap<>(raw);
                env.put("raw", raw); 

                if (parseRule.getMatchExpr() != null && !parseRule.getMatchExpr().isBlank()) {
                    boolean matched = AviatorUtil.evalBoolean(parseRule.getMatchExpr(), env);
                    log.info("[PARSER-DEBUG] matchExpr: [{}], result: {}", parseRule.getMatchExpr(), matched);
                    if (!matched) {
                        return null; // 不匹配则忽略
                    }
                }

                // 1.2 执行解析脚本: Raw -> Parsed
                Object parsedObj = AviatorUtil.eval(parseRule.getParseScript(), env);
                if (!(parsedObj instanceof Map)) {
                    log.warn("[PARSER-DEBUG] !!! 解析脚本返回了非 Map 对象: {}", parsedObj);
                    return null;
                }
                Map<String, Object> data = (Map<String, Object>) parsedObj;
                log.info("[PARSER-DEBUG] parseScript 结果: {}", JSON.toJSONString(data));

                // 1.3 执行映射脚本: Parsed -> Mapped (可选)
                if (parseRule.getMappingScript() != null && !parseRule.getMappingScript().isBlank()) {
                     Map<String, Object> mapEnv = new HashMap<>(data); 
                     mapEnv.put("raw", raw);
                     mapEnv.put("parsed", data); 
                     
                     Object mappedObj = AviatorUtil.eval(parseRule.getMappingScript(), mapEnv);
                     if (mappedObj instanceof Map) {
                         data = (Map<String, Object>) mappedObj;
                         log.info("[PARSER-DEBUG] mappingScript 结果: {}", JSON.toJSONString(data));
                     }
                }
                
                // 1.4 转换为 MetricData 标准格式
                if (data == null || data.isEmpty()) {
                    log.warn("[PARSER-DEBUG] !!! 最终数据为空");
                    return null;
                }
                
                String deviceCode = String.valueOf(data.get("deviceCode"));
                if (deviceCode == null || "null".equals(deviceCode)) {
                    log.warn("[PARSER-DEBUG] !!! 结果缺少 deviceCode: {}", data);
                    return null;
                }

                MetricData metric = new MetricData();
                metric.setDeviceCode(deviceCode);
                metric.setPropertyCode(String.valueOf(data.get("propertyCode")));
                
                Object val = data.get("value");
                if (val instanceof Number) {
                    metric.setValue(((Number) val).doubleValue());
                }
                metric.setStrValue(String.valueOf(val));
                
                Object ts = data.get("ts");
                metric.setTs(ts != null && !"null".equals(String.valueOf(ts)) ? Long.parseLong(String.valueOf(ts)) : System.currentTimeMillis());
                
                log.info("[PARSER-DEBUG] >>> 最终解析成功: {}", JSON.toJSONString(metric));
                return metric;
            }

            // 2. 若未配置脚本，视为无法解析 (纯配置驱动)
            log.debug("未配置解析脚本，忽略报文: ruleId={}", parseRule.getId());
            return null;
        } catch (Exception e) {
            log.error("解析报文失败: rule={}, raw={}", parseRule.getId(), raw, e);
            return null;
        }
    }

    /**
     * 获取匹配设备的告警规则
     */
    private Collection<AlarmRule> getMatchAlarmRules(
            ReadOnlyContext ctx,
            String deviceCode,
            String propertyCode) throws Exception {

        List<AlarmRule> matchedRules = new ArrayList<>();

        for (Map.Entry<String, AlarmRule> entry : ctx.getBroadcastState(ALARM_RULE_STATE_DESC).immutableEntries()) {
            AlarmRule rule = entry.getValue();
            if (rule != null && rule.getEnabled()) {
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
        // AlarmRule.level 是 Integer，AlarmEvent.level 是 AlarmLevel 枚举
        event.setLevel(AlarmLevel.fromLevel(rule.getLevel()));
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
            ctx.getBroadcastState(DEVICE_STATE_DESC).remove(device.getDeviceCode());
            log.info("设备已删除: {}", device.getDeviceCode());
        } else {
            ctx.getBroadcastState(DEVICE_STATE_DESC).put(device.getDeviceCode(), device);
            log.info("设备已更新: {}", device.getDeviceCode());
        }
    }

    /**
     * 处理解析规则变更
     */
    private void handleParseRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        ParseRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), ParseRule.class);
        String key = rule.getGatewayType() + ":" + rule.getVersion();
        if (event.getOp() == ConfigOpType.DELETE) {
            ctx.getBroadcastState(PARSE_RULE_STATE_DESC).remove(key);
            log.info("解析规则已删除: {}", key);
        } else {
            ctx.getBroadcastState(PARSE_RULE_STATE_DESC).put(key, rule);
            log.info("解析规则已更新: {}", key);
        }
    }

    /**
     * 处理告警规则变更
     */
    private void handleAlarmRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        AlarmRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), AlarmRule.class);
        if (event.getOp() == ConfigOpType.DELETE) {
            ctx.getBroadcastState(ALARM_RULE_STATE_DESC).remove(rule.getRuleCode());
            log.info("告警规则已删除: {}", rule.getRuleCode());
        } else {
            ctx.getBroadcastState(ALARM_RULE_STATE_DESC).put(rule.getRuleCode(), rule);
            log.info("告警规则已更新: {}", rule.getRuleCode());
        }
    }

    /**
     * 处理离线规则变更
     */
    private void handleOfflineRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        OfflineRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), OfflineRule.class);
        if (event.getOp() == ConfigOpType.DELETE) {
            ctx.getBroadcastState(OFFLINE_RULE_STATE_DESC).remove(rule.getDeviceCode());
            log.info("离线规则已删除: {}", rule.getDeviceCode());
        } else {
            ctx.getBroadcastState(OFFLINE_RULE_STATE_DESC).put(rule.getDeviceCode(), rule);
            log.info("离线规则已更新: {}", rule.getDeviceCode());
        }
    }
}