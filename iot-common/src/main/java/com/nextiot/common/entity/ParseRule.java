package com.nextiot.common.entity;

import lombok.Data;

/**
 * 解析规则实体
 */
@Data
public class ParseRule {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 网关类型
     */
    private String gatewayType;
    /**
     * 协议类型
     */
    private String protocolType;
    /**
     * 匹配表达式（Aviator 表达式，判断是否命中该规则）
     */
    private String matchExpr;
    /**
     * 解析脚本（Aviator 脚本，从原始报文提取字段）
     */
    private String parseScript;
    /**
     * 映射脚本（Aviator 脚本，映射为标准格式）
     */
    private String mappingScript;
    /**
     * 版本号
     */
    private Integer version;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 创建时间
     */
    private Long createdAt;
    /**
     * 更新时间
     */
    private Long updatedAt;
}