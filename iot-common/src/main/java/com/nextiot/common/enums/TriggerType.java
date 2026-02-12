package com.nextiot.common.enums;

/**
 * 触发类型枚举
 */
public enum TriggerType {
    /**
     * 连续 N 次
     */
    CONTINUOUS_N("CONTINUOUS_N", "连续N次"),
    /**
     * 时间窗口
     */
    WINDOW("WINDOW", "时间窗口");

    private final String code;
    private final String desc;

    TriggerType(String code, String desc) {
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