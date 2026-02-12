package com.nextiot.common.enums;

/**
 * 告警状态枚举
 */
public enum AlarmStatus {
    /**
     * 活跃状态
     */
    ACTIVE("ACTIVE", "活跃"),
    /**
     * 已确认
     */
    ACKED("ACKED", "已确认"),
    /**
     * 已恢复
     */
    RECOVERED("RECOVERED", "已恢复");

    private final String code;
    private final String desc;

    AlarmStatus(String code, String desc) {
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