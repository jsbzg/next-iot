package com.nextiot.common.entity;

import lombok.Data;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.enums.AlarmLevel;

/**
 * 告警规则实体
 */
@Data
public class AlarmRule {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 规则编码
     */
    private String ruleCode;
    /**
     * 设备编码（可为空，表示按模型）
     */
    private String deviceCode;
    /**
     * 点位编码
     */
    private String propertyCode;
    /**
     * 条件表达式（Aviator 表达式，如 value > 80）
     */
    private String conditionExpr;
    /**
     * 触发类型：连续N次 / 时间窗口
     */
    private TriggerType triggerType;
    /**
     * 触发参数N值（连续N次 或 窗口内次数）
     */
    private Integer triggerN;
    /**
     * 窗口大小（秒，仅 WINDOW 类型有效）
     */
    private Integer windowSeconds;
    /**
     * 流内抑制时间（秒）
     */
    private Integer suppressSeconds;
    /**
     * 告警级别
     */
    private AlarmLevel level;
    /**
     * 告警描述
     */
    private String description;
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