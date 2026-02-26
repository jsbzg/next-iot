package com.nextiot.common.entity;

import lombok.Data;

import java.util.Map;

/**
 * 中间原始消息包装类
 * 保持原始报文为 String，所有格式判断、字段提取等逻辑由 AviatorScript 完成
 */
@Data
public class IntermediateRawMessage {
    /**
     * 原始消息字符串
     * 保持 String 格式，供 AviatorScript 动态解析
     */
    private String rawMessage;

    /**
     * 网关类型（在数据源层提取）
     * 用于匹配合适的 ParseRule
     */
    private String gatewayType;

    /**
     * 设备编码（可选，预提取）
     * 如果在数据源层可以提取到，可用于优化 keyBy 策略
     */
    private String deviceCode;

    /**
     * 接收时间戳
     */
    private Long receiveTime;

    /**
     * 消息元数据
     * 用于存储额外的元信息，如来源分区、原始 offset 等
     */
    private Map<String, Object> metadata;
}
