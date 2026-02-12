package com.nextiot.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.config.mapper.AlarmRuleMapper;
import com.nextiot.config.producer.ConfigEventProducer;
import com.nextiot.config.service.AlarmRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 告警规则管理服务实现
 */
@Service
public class AlarmRuleServiceImpl extends ServiceImpl<AlarmRuleMapper, AlarmRule> implements AlarmRuleService {

    @Resource
    private ConfigEventProducer configEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmRule createAlarmRule(AlarmRule alarmRule) {
        // 校验规则编码唯一性
        AlarmRule existing = getByRuleCode(alarmRule.getRuleCode());
        if (existing != null) {
            throw new RuntimeException("规则编码已存在: " + alarmRule.getRuleCode());
        }

        // 设置默认值
        alarmRule.setCreatedAt(System.currentTimeMillis());
        alarmRule.setUpdatedAt(System.currentTimeMillis());
        if (alarmRule.getEnabled() == null) {
            alarmRule.setEnabled(true);
        }
        if (alarmRule.getSuppressSeconds() == null) {
            alarmRule.setSuppressSeconds(60); // 默认抑制 60 秒
        }

        // 保存到数据库
        save(alarmRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.ALARM_RULE, ConfigOpType.ADD, alarmRule);

        return alarmRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmRule updateAlarmRule(AlarmRule alarmRule) {
        // 更新数据库
        alarmRule.setUpdatedAt(System.currentTimeMillis());
        updateById(alarmRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.ALARM_RULE, ConfigOpType.UPDATE, alarmRule);

        return alarmRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAlarmRule(String ruleCode) {
        // 查询规则
        AlarmRule alarmRule = getByRuleCode(ruleCode);
        if (alarmRule != null) {
            // 删除数据库记录
            removeById(alarmRule.getId());

            // 发送配置变更事件
            configEventProducer.sendConfigChangeEvent(ConfigChangeType.ALARM_RULE, ConfigOpType.DELETE, alarmRule);
        }
    }

    @Override
    public AlarmRule getByRuleCode(String ruleCode) {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRule::getRuleCode, ruleCode);
        return getOne(wrapper);
    }

    @Override
    public List<AlarmRule> getAllAlarmRules() {
        return list();
    }

    @Override
    public List<AlarmRule> getMatchingRules(String deviceCode, String propertyCode) {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRule::getPropertyCode, propertyCode)
                .eq(AlarmRule::getEnabled, true)
                .and(w -> w.eq(AlarmRule::getDeviceCode, deviceCode)
                        .or()
                        .isNull(AlarmRule::getDeviceCode)); // deviceCode 为空表示按模型配置
        return list(wrapper);
    }
}