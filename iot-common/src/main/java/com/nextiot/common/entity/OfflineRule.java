package com.nextiot.common.entity;

import lombok.Data;

/**
 * 离线规则实体
 */
@Data
public class OfflineRule {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 超时时长（秒）
     */
    private Integer timeoutSeconds;
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