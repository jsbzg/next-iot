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
     * 解析模式：STRING（新模式，String输入）或 LEGACY（旧模式，Map输入）
     */
    private String parseMode = "STRING";
    /**
     * Key 提取脚本（用于提前从原始报文中提取 deviceCode）
     * 可用于优化 keyBy 策略
     */
    private String keyExtractorScript;
    /**
     * 解析器类型提示（JSON/CSV/PIPE/FIXED_WIDTH/HEX/CUSTOM）
     * 用于前端展示和脚本模板选择
     */
    private String parserType;
    /**
     * 示例输入（用于规则调试和前端展示）
     */
    private String sampleInput;
    /**
     * 验证正则表达式（用于验证原始报文格式）
     */
    private String validationRegex;
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