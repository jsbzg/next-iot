package com.nextiot.common.entity;

import lombok.Data;

/**
 * 设备实例实体（设备白名单主数据）
 */
@Data
public class ThingDevice {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 设备编码（唯一）
     */
    private String deviceCode;
    /**
     * 所属物模型编码
     */
    private String modelCode;
    /**
     * 网关类型
     */
    private String gatewayType;
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 在线状态
     */
    private Boolean online;
    /**
     * 最后上报时间
     */
    private Long lastSeenTs;
    /**
     * 创建时间
     */
    private Long createdAt;
    /**
     * 更新时间
     */
    private Long updatedAt;
}