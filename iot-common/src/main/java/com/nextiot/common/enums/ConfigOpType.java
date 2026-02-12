package com.nextiot.common.enums;

/**
 * 配置变更操作类型
 */
public enum ConfigOpType {
    /**
     * 新增
     */
    ADD("ADD", "新增"),
    /**
     * 修改
     */
    UPDATE("UPDATE", "修改"),
    /**
     * 删除
     */
    DELETE("DELETE", "删除");

    private final String code;
    private final String desc;

    ConfigOpType(String code, String desc) {
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