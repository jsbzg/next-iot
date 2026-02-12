package com.nextiot.common.enums;

/**
 * 网关类型枚举
 */
public enum GatewayType {

    /**
     * MQTT 协议
     */
    MQTT("mqtt"),

    /**
     * HTTP 协议
     */
    HTTP("http"),

    /**
     * TCP 协议
     */
    TCP("tcp");

    private final String code;

    GatewayType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据编码获取枚举
     * @param code 网关类型编码
     * @return 网关类型枚举
     */
    public static GatewayType fromCode(String code) {
        for (GatewayType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown gateway type: " + code);
    }
}
