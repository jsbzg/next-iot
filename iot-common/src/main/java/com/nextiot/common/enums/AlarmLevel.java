package com.nextiot.common.enums;

/**
 * 告警级别枚举
 */
public enum AlarmLevel {
    /**
     * 提示级别
     */
    INFO(0, "提示"),
    /**
     * 警告级别
     */
    WARNING(1, "警告"),
    /**
     * 严重级别
     */
    CRITICAL(2, "严重"),
    /**
     * 紧急级别
     */
    EMERGENCY(3, "紧急");

    private final int level;
    private final String desc;

    AlarmLevel(int level, String desc) {
        this.level = level;
        this.desc = desc;
    }

    public int getLevel() {
        return level;
    }

    public String getDesc() {
        return desc;
    }
}