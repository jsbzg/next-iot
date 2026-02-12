package com.nextiot.common.entity;

import lombok.Data;

/**
 * 物模型实体
 */
@Data
public class ThingModel {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 模型编码（唯一）
     */
    private String modelCode;
    /**
     * 模型名称
     */
    private String modelName;
    /**
     * 创建时间
     */
    private Long createdAt;
    /**
     * 更新时间
     */
    private Long updatedAt;
}