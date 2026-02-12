package com.nextiot.flink.state;

import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.entity.ThingDevice;

/**
 * Broadcast State Key 定义
 */
public class BroadcastStateKey {

    /**
     * 设备白名单 State Key
     */
    public static final String DEVICE_STATE = "DEVICE_STATE";

    /**
     * 解析规则 State Key
     */
    public static final String PARSE_RULE_STATE = "PARSE_RULE_STATE";

    /**
     * 告警规则 State Key
     */
    public static final String ALARM_RULE_STATE = "ALARM_RULE_STATE";

    /**
     * 离线规则 State Key
     */
    public static final String OFFLINE_RULE_STATE = "OFFLINE_RULE_STATE";

    /**
     * 生成设备主数据 State Key
     */
    public static String deviceKey(String deviceCode) {
        return "device:" + deviceCode;
    }

    /**
     * 生成解析规则 State Key
     * Key 格式：gatewayType:version
     */
    public static String parseRuleKey(String gatewayType, Integer version) {
        return "parse:" + gatewayType + ":" + version;
    }

    /**
     * 生成告警规则 State Key
     * Key 格式：deviceCode:propertyCode 或 modelCode:propertyCode
     */
    public static String alarmRuleKey(String deviceCode, String propertyCode) {
        return "alarm:" + deviceCode + ":" + propertyCode;
    }

    /**
     * 生成离线规则 State Key
     */
    public static String offlineRuleKey(String deviceCode) {
        return "offline:" + deviceCode;
    }
}