package com.nextiot.common.enums;

/**
 * 数据类型枚举
 */
public enum DataType {
    /**
     * 整数类型
     */
    INT("int", "整数"),
    /**
     * 浮点类型
     */
    DOUBLE("double", "浮点数"),
    /**
     * 字符串类型
     */
    STRING("string", "字符串"),
    /**
     * 布尔类型
     */
    BOOLEAN("bool", "布尔值");

    private final String code;
    private final String desc;

    DataType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据代码获取枚举
     */
    public static DataType fromCode(String code) {
        for (DataType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return STRING;
    }
}