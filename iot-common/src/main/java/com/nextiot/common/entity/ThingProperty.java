package com.nextiot.common.entity;

import lombok.Data;
import com.nextiot.common.enums.DataType;

/**
 * 点位定义实体
 */
@Data
public class ThingProperty {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 所属物模型编码
     */
    private String modelCode;
    /**
     * 点位编码
     */
    private String propertyCode;
    /**
     * 点位名称
     */
    private String propertyName;
    /**
     * 数据类型
     */
    private DataType dataType;
    /**
     * 单位
     */
    private String unit;
    /**
     * 创建时间
     */
    private Long createdAt;
    /**
     * 更新时间
     */
    private Long updatedAt;
}