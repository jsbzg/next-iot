package com.nextiot.common.enums;

/**
 * 配置变更类型
 */
public enum ConfigChangeType {
    /**
     * 设备变更
     */
    DEVICE("DEVICE", "设备主数据"),
    /**
     * 解析规则变更
     */
    PARSE_RULE("PARSE_RULE", "解析规则"),
    /**
     * 告警规则变更
     */
    ALARM_RULE("ALARM_RULE", "告警规则"),
    /**
     * 离线规则变更
     */
    OFFLINE_RULE("OFFLINE_RULE", "离线规则");

    private final String code;
    private final String desc;

    ConfigChangeType(String code, String desc) {
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