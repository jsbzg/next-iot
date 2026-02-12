package com.nextiot.flink.state;

import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.entity.ThingDevice;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;

import java.io.Serializable;

/**
 * Broadcast State Key 定义
 * 用于 Flink 动态规则刷新机制
 */
public class BroadcastStateKeys implements Serializable {

    /**
     * 设备状态存储 Key 前缀
     */
    public static final String DEVICE_PREFIX = "device:";

    /**
     * 解析规则状态存储 Key 前缀
     */
    public static final String PARSE_RULE_PREFIX = "parse_rule:";

    /**
     * 告警规则状态存储 Key 前缀
     */
    public static final String ALARM_RULE_PREFIX = "alarm_rule:";

    /**
     * 离线规则状态存储 Key 前缀
     */
    public static final String OFFLINE_RULE_PREFIX = "offline_rule:";

    /**
     * 构建设备 Key
     */
    public static String deviceKey(String deviceCode) {
        return DEVICE_PREFIX + deviceCode;
    }

    /**
     * 构建解析规则 Key
     */
    public static String parseRuleKey(String gatewayType, Integer version) {
        return PARSE_RULE_PREFIX + gatewayType + ":" + version;
    }

    /**
     * 构建告警规则 Key
     */
    public static String alarmRuleKey(String ruleCode) {
        return ALARM_RULE_PREFIX + ruleCode;
    }

    /**
     * 构建离线规则 Key
     */
    public static String offlineRuleKey(String deviceCode) {
        return OFFLINE_RULE_PREFIX + deviceCode;
    }

    /**
     * 更新设备状态到 Broadcast State
     */
    public static void updateDevice(BroadcastState<String, ThingDevice> state, ThingDevice device) throws Exception {
        String key = deviceKey(deviceCode(device));
        state.put(key, device);
    }

    /**
     * 删除设备状态
     */
    public static void deleteDevice(BroadcastState<String, ThingDevice> state, String deviceCode) throws Exception {
        String key = deviceKey(deviceCode);
        state.remove(key);
    }

    /**
     * 获取设备状态
     */
    public static ThingDevice getDevice(ReadOnlyBroadcastState<String, ThingDevice> state, String deviceCode) throws Exception {
        String key = deviceKey(deviceCode);
        return state.get(key);
    }

    /**
     * 更新解析规则状态
     */
    public static void updateParseRule(BroadcastState<String, ParseRule> state, ParseRule rule) throws Exception {
        String key = parseRuleKey(rule.getGatewayType(), rule.getVersion());
        state.put(key, rule);
    }

    /**
     * 获取解析规则
     */
    public static ParseRule getParseRule(ReadOnlyBroadcastState<String, ParseRule> state, String gatewayType) throws Exception {
        // 获取最新版本的规则
        String keyPrefix = PARSE_RULE_PREFIX + gatewayType + ":";
        int maxVersion = 0;
        String latestKey = null;

        for (String key : state.keys()) {
            if (key.startsWith(keyPrefix)) {
                String versionStr = key.substring(keyPrefix.length());
                int version = Integer.parseInt(versionStr);
                if (version > maxVersion) {
                    maxVersion = version;
                    latestKey = key;
                }
            }
        }
        return latestKey != null ? state.get(latestKey) : null;
    }

    /**
     * 更新告警规则状态
     */
    public static void updateAlarmRule(BroadcastState<String, AlarmRule> state, AlarmRule rule) throws Exception {
        String key = alarmRuleKey(rule.getRuleCode());
        state.put(key, rule);
    }

    /**
     * 删除告警规则状态
     */
    public static void deleteAlarmRule(BroadcastState<String, AlarmRule> state, String ruleCode) throws Exception {
        String key = alarmRuleKey(ruleCode);
        state.remove(key);
    }

    /**
     * 获取告警规则
     */
    public static AlarmRule getAlarmRule(ReadOnlyBroadcastState<String, AlarmRule> state, String ruleCode) throws Exception {
        String key = alarmRuleKey(ruleCode);
        return state.get(key);
    }

    /**
     * 更新离线规则状态
     */
    public static void updateOfflineRule(BroadcastState<String, OfflineRule> state, OfflineRule rule) throws Exception {
        String key = offlineRuleKey(rule.getDeviceCode());
        state.put(key, rule);
    }

    /**
     * 删除离线规则状态
     */
    public static void deleteOfflineRule(BroadcastState<String, OfflineRule> state, String deviceCode) throws Exception {
        String key = offlineRuleKey(deviceCode);
        state.remove(key);
    }

    /**
     * 获取离线规则
     */
    public static OfflineRule getOfflineRule(ReadOnlyBroadcastState<String, OfflineRule> state, String deviceCode) throws Exception {
        String key = offlineRuleKey(deviceCode);
        return state.get(key);
    }
}