package com.nextiot.flink.function;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.*;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.util.AviatorUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.state.*;
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
 * <p>
 * 功能：
 * 1. 动态解析（ParseRule + AviatorScript）- 支持 JSON/CSV/PIPE/固定长度等多种格式
 * 2. 设备合法性校验（ThingDevice 广播状态）- 不在库 → Side Output
 * 3. 动态检测规则（AlarmRule + Aviator）- 连续N次 / 窗口
 * 4. 流内抑制（State 防刷屏/窗口去重）
 * 5. 离线检测（OfflineRule + 定时器）
 */
public class IotDataProcessFunction extends KeyedBroadcastProcessFunction<
        String,                           // Key (gatewayType_hash)
        IntermediateRawMessage,          // 主流：原始消息包装（保持 String）
        ConfigChangeEvent,                // 广播流：配置变更事件
        Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> {  // 输出：(Metrics, AlarmEvent, OfflineEvent, DeviceCode)

    private static final Logger log = LoggerFactory.getLogger(IotDataProcessFunction.class);

    // 非法设备数据侧输出
    public static final OutputTag<String> DIRTY_DATA_OUTPUT = new OutputTag<String>("dirty-data") {
    };

    // ========== Broadcast State 描述符（MapStateDescriptor）==========
    // 设备状态：key=deviceCode, value=ThingDevice
    public static final MapStateDescriptor<String, ThingDevice> DEVICE_STATE_DESC =
            new MapStateDescriptor<>("device-state", Types.STRING, TypeInformation.of(ThingDevice.class));

    // 解析规则状态：key=gatewayType:version, value=ParseRule
    public static final MapStateDescriptor<String, ParseRule> PARSE_RULE_STATE_DESC =
            new MapStateDescriptor<>("parse-rule-state", Types.STRING, TypeInformation.of(ParseRule.class));

    // 告警规则状态：key=ruleCode, value=AlarmRule
    public static final MapStateDescriptor<String, List<AlarmRule>> ALARM_RULE_STATE_DESC =
            new MapStateDescriptor<>("alarm-rule-state", Types.STRING, Types.LIST(TypeInformation.of(AlarmRule.class)));

    // 离线规则状态：key=deviceCode, value=OfflineRule
    public static final MapStateDescriptor<String, OfflineRule> OFFLINE_RULE_STATE_DESC =
            new MapStateDescriptor<>("offline-rule-state", Types.STRING, TypeInformation.of(OfflineRule.class));

    // ========== Keyed State ==========
    // 连续次数：ruleCode -> count
    private MapState<String, Integer> continuousCountState;

    // 窗口触发时间：ruleCode -> List<Long>
    private MapState<String, List<Long>> windowTriggerTimesState;

    // 抑制时间：ruleCode -> lastTriggerTs
    private MapState<String, Long> suppressState;

    // 设备最后上报时间状态
    private ValueState<Long> lastSeenTimeState;

    // 已注册的离线 timer 时间
    private ValueState<Long> registeredOfflineTimerState;

    // ========= Aviator 预编译缓存（简单示意） =========
    // key = 表达式字符串
    private final Map<String, Object> compiledExprCache = new HashMap<>();

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        continuousCountState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("continuousCount", Types.STRING, Types.INT));

        windowTriggerTimesState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("windowTriggerTimes", Types.STRING, Types.LIST(Types.LONG)));

        suppressState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("suppressTime", Types.STRING, Types.LONG));

        lastSeenTimeState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("lastSeenTime", Types.LONG));

        registeredOfflineTimerState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("registeredOfflineTimer", Types.LONG));
    }

    /**
     * 处理主流：原始报文（包装在 IntermediateRawMessage 中）
     */
    @Override
    public void processElement(
            IntermediateRawMessage msg,
            ReadOnlyContext ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        long now = System.currentTimeMillis();
        String rawMessage = msg.getRawMessage();
        String gatewayType = msg.getGatewayType();

        log.info("[PARSER-DEBUG] >>> 收到原始消息: gatewayType={}, rawMessage={}", gatewayType, rawMessage);

        // ===== Step 1: 获取解析规则 =====
        ParseRule parseRule = ctx.getBroadcastState(PARSE_RULE_STATE_DESC).get(gatewayType);
        if (parseRule == null) {
            log.warn("[PARSER-DEBUG] !!! 未找到解析规则: gatewayType={}", gatewayType);
            String dirtyMsg = String.format("未找到解析规则: gatewayType=%s, raw=%s",
                    gatewayType, rawMessage);
            ctx.output(DIRTY_DATA_OUTPUT, dirtyMsg);
            return;
        }

        // 规则验证正则表达式（如果配置）
        if (parseRule.getValidationRegex() != null && !parseRule.getValidationRegex().isBlank()) {
            if (!rawMessage.matches(parseRule.getValidationRegex())) {
                log.warn("[PARSER-DEBUG] !!! 验证正则不匹配: regex={}, raw={}", parseRule.getValidationRegex(), rawMessage);
                ctx.output(DIRTY_DATA_OUTPUT, "验证正则不匹配: " + rawMessage);
                return;
            }
        }

        // ===== Step 2: 动态解析（支持返回 List<MetricData>）=====
        List<MetricData> metrics = parseRawData(rawMessage, parseRule);
        if (metrics.isEmpty()) {
            log.warn("[PARSER-DEBUG] !!! 解析结果为空或被 matchExpr 过滤: raw={}", rawMessage);
            ctx.output(DIRTY_DATA_OUTPUT, "解析失败: " + rawMessage);
            return;
        }

        log.info("[PARSER-DEBUG] >>> 解析成功，产出 {} 条数据", metrics.size());

        // ===== Step 3: 遍历每个 MetricData 进行处理 =====
        for (MetricData metricData : metrics) {
            processSingleMetric(metricData, ctx, out);
        }
    }

    /**
     * 处理单个 MetricData
     */
    private void processSingleMetric(
            MetricData metricData,
            ReadOnlyContext ctx,
            Collector<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> out) throws Exception {

        long now = System.currentTimeMillis();
        String deviceCode = metricData.getDeviceCode();

        log.info("[PARSER-DEBUG] >>> 处理指标: deviceCode={}, propertyCode={}, value={}",
                deviceCode, metricData.getPropertyCode(), metricData.getValue());

        // ===== Step 1: 设备合法性校验（第一道业务闸门）=====
        ThingDevice device = ctx.getBroadcastState(DEVICE_STATE_DESC).get(deviceCode);
        if (device == null) {
            log.warn("[PARSER-DEBUG] !!! 非法设备(未注册): deviceCode={}", deviceCode);
            String dirtyMsg = String.format("非法设备数据: deviceCode=%s, metric=%s",
                    deviceCode, JSON.toJSONString(metricData));
            ctx.output(DIRTY_DATA_OUTPUT, dirtyMsg);
            return;
        }
        log.info("[PARSER-DEBUG] >>> 设备校验通过: {}", deviceCode);

        // 更新设备最后上报时间
        lastSeenTimeState.update(now);

        // ===== Step 2: 设备离线检测设置 =====
        OfflineRule offlineRule = ctx.getBroadcastState(OFFLINE_RULE_STATE_DESC).get(deviceCode);
        if (offlineRule != null && offlineRule.getEnabled()) {
            long offlineTriggerTime = now + offlineRule.getTimeoutSeconds() * 1000L;
            Long registered = registeredOfflineTimerState.value();
            // 为每个设备只保留"最新的一次离线检测定时器"，避免随着数据上报频率无限堆 Timer
            if (registered == null || offlineTriggerTime > registered) {
                ctx.timerService().registerProcessingTimeTimer(offlineTriggerTime);
                registeredOfflineTimerState.update(offlineTriggerTime);
            }
        }

        // ===== Step 3: 告警规则检测 =====
        String propertyCode = metricData.getPropertyCode();
        List<AlarmRule> rules = ctx.getBroadcastState(ALARM_RULE_STATE_DESC).get(propertyCode);
        AlarmEvent alarmEvent = null;

        if (rules != null) {
            for (AlarmRule rule : rules) {
                if (!rule.getEnabled()) {
                    continue;
                }

                // 条件判断（Aviator 表达式）
                Map<String, Object> env = new HashMap<>();
                env.put("value", metricData.getValue());
                boolean matched = AviatorUtil.evalBoolean(rule.getConditionExpr(), env);
                if (!matched) {
                    continue;
                }

                // ===== Step 3.1: 触发类型判断 =====
                boolean shouldTrigger = false;
                String ruleCode = rule.getRuleCode();
                String triggerType = rule.getTriggerType();
                if (TriggerType.CONTINUOUS_N.getCode().equals(triggerType)) {
                    // 连续 N 次判断
                    Integer count = continuousCountState.get(ruleCode);
                    if (count == null) {
                        count = 0;
                    }
                    count++;
                    if (count >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        continuousCountState.remove(ruleCode);
                    } else {
                        continuousCountState.put(ruleCode, count);
                    }
                } else if (TriggerType.WINDOW.getCode().equals(triggerType)) {
                    // 窗口判断：统计窗口内的触发次数
                    long windowMs = rule.getWindowSeconds() * 1000L;
                    List<Long> times = windowTriggerTimesState.get(ruleCode);
                    if (times == null) times = new ArrayList<>();

                    List<Long> valid = new ArrayList<>();
                    for (Long ts : times) {
                        if (now - ts <= windowMs) {
                            valid.add(ts);
                        }
                    }
                    valid.add(now);
                    if (valid.size() >= rule.getTriggerN()) {
                        shouldTrigger = true;
                        windowTriggerTimesState.remove(ruleCode);
                    } else {
                        windowTriggerTimesState.put(ruleCode, valid);
                    }
                }

                // ===== Step 3.2: 流内抑制（防止刷屏）=====
                if (shouldTrigger) {
                    Long lastTriggerTime = suppressState.get(ruleCode);
                    if (lastTriggerTime != null && now - lastTriggerTime < rule.getSuppressSeconds() * 1000L) {
                        log.debug("告警被抑制: ruleCode={}, deviceCode={}", rule.getRuleCode(), deviceCode);
                        continue;
                    }
                    // 更新抑制时间
                    suppressState.put(ruleCode, now);
                    // 构造告警事件
                    alarmEvent = buildAlarmEvent(rule, deviceCode, metricData, now);
                    break; // 同一规则只触发一次
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
     * 解析原始报文为标准格式
     * 支持多种格式：JSON 对象/数组、CSV、PIPE、固定长度等
     *
     * @param rawMessage 原始消息字符串
     * @param parseRule 解析规则
     * @return 解析后的 MetricData 列表（可能为空）
     */
    private List<MetricData> parseRawData(String rawMessage, ParseRule parseRule) {
        try {
            log.info("[PARSER-DEBUG] 规则检测开始: 规则ID={}, GatewayType={}, ParseMode={}",
                    parseRule.getId(), parseRule.getGatewayType(), parseRule.getParseMode());

            // Step 1: 构建 Aviator 环境
            Map<String, Object> env = buildParseEnv(rawMessage);

            // Step 2: 执行 matchExpr
            if (!checkMatchExpr(parseRule, env)) {
                log.info("[PARSER-DEBUG] matchExpr 不匹配: [{}], rawMessage={}",
                        parseRule.getMatchExpr(), rawMessage);
                return Collections.emptyList();
            }

            // Step 3: 执行 parseScript
            Object parsed = AviatorUtil.eval(parseRule.getParseScript(), env);
            if (parsed == null) {
                log.warn("[PARSER-DEBUG] parseScript 返回 null");
                return Collections.emptyList();
            }

            log.info("[PARSER-DEBUG] parseScript 返回类型: {}, value={}",
                    parsed.getClass().getSimpleName(), JSON.toJSONString(parsed));

            // Step 4: 执行 mappingScript（可选）
            parsed = applyMappingScript(parsed, rawMessage, parseRule);

            // Step 5: 规范化为 List<MetricData>
            List<MetricData> metrics = normalizeToMetricDataList(parsed, parseRule);

            log.info("[PARSER-DEBUG] >>> 规范化完成，产出 {} 条 MetricData", metrics.size());
            for (MetricData m : metrics) {
                log.info("[PARSER-DEBUG]   MetricData: {}", JSON.toJSONString(m));
            }

            return metrics;

        } catch (Exception e) {
            log.error("[PARSER-DEBUG] 解析失败: rule={}, raw={}", parseRule.getId(), rawMessage, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建 Aviator 解析环境
     * 提供 rawMessage 和向后兼容的 rawMap
     */
    private Map<String, Object> buildParseEnv(String rawMessage) {
        Map<String, Object> env = new HashMap<>();
        env.put("rawMessage", rawMessage);
        env.put("now", System.currentTimeMillis());

        // 尝试 JSON 解析（向后兼容旧脚本）
        try {
            Map<String, Object> rawMap = JSON.parseObject(rawMessage, Map.class);
            env.put("rawMap", rawMap);
            env.put("raw", rawMap); // Legacy support
            // 扁平化字段，方便脚本直接访问（如 rawMap.deviceCode -> deviceCode）
            env.putAll(rawMap);
        } catch (Exception ignored) {
            // 不是 JSON 格式，提供 null
            env.put("rawMap", null);
            env.put("raw", null);
        }

        return env;
    }

    /**
     * 检查 matchExpr 是否匹配
     */
    private boolean checkMatchExpr(ParseRule parseRule, Map<String, Object> env) {
        if (parseRule.getMatchExpr() == null || parseRule.getMatchExpr().isBlank()) {
            return true; // 未配置 matchExpr，默认匹配
        }
        return AviatorUtil.evalBoolean(parseRule.getMatchExpr(), env);
    }

    /**
     * 应用 mappingScript（可选）
     */
    private Object applyMappingScript(Object parsed, String rawMessage, ParseRule parseRule) {
        if (parseRule.getMappingScript() == null || parseRule.getMappingScript().isBlank()) {
            return parsed;
        }

        try {
            Map<String, Object> mapEnv = new HashMap<>();
            mapEnv.put("rawMessage", rawMessage);
            mapEnv.put("parsed", parsed);
            mapEnv.put("now", System.currentTimeMillis());

            // 向后兼容：如果 parsed 是 Map，也放入环境
            if (parsed instanceof Map) {
                mapEnv.putAll((Map<String, Object>) parsed);
            }

            Object mapped = AviatorUtil.eval(parseRule.getMappingScript(), mapEnv);
            return mapped != null ? mapped : parsed; // 如果返回 null，使用原始值
        } catch (Exception e) {
            log.warn("[PARSER-DEBUG] mappingScript 执行失败，使用原始值: {}", e.getMessage());
            return parsed;
        }
    }

    /**
     * 将解析结果规范化为 List<MetricData>
     * 支持输入类型：
     * - Map: 单个 MetricData
     * - List<Map>: 多个 MetricData
     */
    private List<MetricData> normalizeToMetricDataList(Object result, ParseRule parseRule) {
        List<MetricData> metrics = new ArrayList<>();

        if (result instanceof Map) {
            // 单个 Map
            MetricData metric = buildMetricData((Map<String, Object>) result);
            if (metric != null) {
                metrics.add(metric);
            }
        } else if (result instanceof List) {
            // List<Map> 或 List<Object>
            for (Object item : (List<?>) result) {
                if (item instanceof Map) {
                    MetricData metric = buildMetricData((Map<String, Object>) item);
                    if (metric != null) {
                        metrics.add(metric);
                    }
                }
            }
        }

        return metrics;
    }

    /**
     * 从 Map 构建 MetricData
     */
    private MetricData buildMetricData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        Object deviceCodeObj = data.get("deviceCode");
        if (deviceCodeObj == null || "null".equals(String.valueOf(deviceCodeObj))) {
            log.warn("[PARSER-DEBUG] !!! 结果缺少 deviceCode: {}", data);
            return null;
        }

        String deviceCode = String.valueOf(deviceCodeObj);
        Object propertyCodeObj = data.get("propertyCode");
        if (propertyCodeObj == null || "null".equals(String.valueOf(propertyCodeObj))) {
            log.warn("[PARSER-DEBUG] !!! 结果缺少 propertyCode: {}", data);
            return null;
        }

        MetricData metric = new MetricData();
        metric.setDeviceCode(deviceCode);
        metric.setPropertyCode(String.valueOf(propertyCodeObj));

        Object val = data.get("value");
        if (val instanceof Number) {
            metric.setValue(((Number) val).doubleValue());
        }
        metric.setStrValue(String.valueOf(val));

        Object ts = data.get("ts");
        metric.setTs(ts != null && !"null".equals(String.valueOf(ts))
                ? Long.parseLong(String.valueOf(ts))
                : System.currentTimeMillis());

        return metric;
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
        if (event.getOp() == ConfigOpType.DELETE) {
            ctx.getBroadcastState(PARSE_RULE_STATE_DESC).remove(rule.getGatewayType());
            log.info("解析规则已删除: {}", rule.getGatewayType());
        } else {
            ctx.getBroadcastState(PARSE_RULE_STATE_DESC).put(rule.getGatewayType(), rule);
            log.info("解析规则已更新: {}", rule.getGatewayType());
        }
    }

    /**
     * 处理告警规则变更
     */
    private void handleAlarmRuleChange(ConfigChangeEvent event, Context ctx) throws Exception {
        AlarmRule rule = JSON.parseObject(JSON.toJSONString(event.getPayload()), AlarmRule.class);
        String propertyCode = rule.getPropertyCode();
        BroadcastState<String, List<AlarmRule>> state = ctx.getBroadcastState(ALARM_RULE_STATE_DESC);
        List<AlarmRule> list = state.get(propertyCode);
        if (list == null) {
            list = new ArrayList<>();
        }
        if (event.getOp() == ConfigOpType.DELETE) {
            list.removeIf(r -> r.getRuleCode().equals(rule.getRuleCode()));
            log.info("告警规则已删除: {}", rule.getRuleCode());
        } else {
            list.removeIf(r -> r.getRuleCode().equals(rule.getRuleCode()));
            list.add(rule);
            log.info("告警规则已更新: {}", rule.getRuleCode());
        }
        if (list.isEmpty()) {
            state.remove(propertyCode);
        } else {
            state.put(propertyCode, list);
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