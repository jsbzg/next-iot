package com.nextiot.common.enums;

/**
 * 配置类型枚举 - 用于 Kafka 配置变更事件
 */
public enum ConfigType {
    /**
     * 设备配置
     */
    DEVICE("DEVICE", "设备配置"),
    /**
     * 解析规则
     */
    PARSE_RULE("PARSE_RULE", "解析规则"),
    /**
     * 告警规则
     */
    ALARM_RULE("ALARM_RULE", "告警规则"),
    /**
     * 离线规则
     */
    OFFLINE_RULE("OFFLINE_RULE", "离线规则");

    private final String code;
    private final String desc;

    ConfigType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}