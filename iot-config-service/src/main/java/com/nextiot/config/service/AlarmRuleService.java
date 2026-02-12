package com.nextiot.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.AlarmRule;

import java.util.List;

/**
 * 告警规则管理服务接口
 */
public interface AlarmRuleService extends IService<AlarmRule> {

    /**
     * 创建告警规则并发送配置变更事件
     */
    AlarmRule createAlarmRule(AlarmRule alarmRule);

    /**
     * 更新告警规则并发送配置变更事件
     */
    AlarmRule updateAlarmRule(AlarmRule alarmRule);

    /**
     * 删除告警规则并发送配置变更事件
     */
    void deleteAlarmRule(String ruleCode);

    /**
     * 根据规则编码获取告警规则
     */
    AlarmRule getByRuleCode(String ruleCode);

    /**
     * 获取所有告警规则
     */
    List<AlarmRule> getAllAlarmRules();

    /**
     * 根据设备编码和点位编码获取匹配的告警规则
     */
    List<AlarmRule> getMatchingRules(String deviceCode, String propertyCode);
}