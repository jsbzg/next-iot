package com.nextiot.common.entity;

import lombok.Data;

/**
 * 告警实例实体（Alarm Service 存储）
 * 可替换点：当前使用 MySQL，后续应迁移 Redis / ES
 */
@Data
public class AlarmInstance {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 告警规则编码
     */
    private String ruleCode;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 点位编码
     */
    private String propertyCode;
    /**
     * 告警状态：ACTIVE/ACKED/RECOVERED
     */
    private String status;
    /**
     * 告警级别
     */
    private Integer level;
    /**
     * 告警描述
     */
    private String description;
    /**
     * 首次触发时间
     */
    private Long firstTriggerTime;
    /**
     * 最后触发时间
     */
    private Long lastTriggerTime;
    /**
     * 确认时间
     */
    private Long ackTime;
    /**
     * 恢复时间
     */
    private Long recoveredTime;
    /**
     * 确认用户
     */
    private String ackUser;
    /**
     * 创建时间
     */
    private Long createdAt;
    /**
     * 更新时间
     */
    private Long updatedAt;
}