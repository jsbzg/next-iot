package com.nextiot.common.entity;

import lombok.Data;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;

import java.util.Map;

/**
 * 配置变更事件（Kafka 消息模型）
 */
@Data
public class ConfigChangeEvent {
    /**
     * 变更类型：DEVICE / PARSE_RULE / ALARM_RULE / OFFLINE_RULE
     */
    private ConfigChangeType type;
    /**
     * 操作类型：ADD / UPDATE / DELETE
     */
    private ConfigOpType op;
    /**
     * 变更内容（JSON 格式的具体数据）
     */
    private Map<String, Object> payload;
    /**
     * 事件时间戳
     */
    private Long timestamp;
}