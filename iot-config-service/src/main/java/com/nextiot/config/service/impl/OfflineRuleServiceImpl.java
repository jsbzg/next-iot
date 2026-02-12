package com.nextiot.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.config.mapper.OfflineRuleMapper;
import com.nextiot.config.producer.ConfigEventProducer;
import com.nextiot.config.service.OfflineRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 离线规则管理服务实现
 */
@Service
public class OfflineRuleServiceImpl extends ServiceImpl<OfflineRuleMapper, OfflineRule> implements OfflineRuleService {

    @Resource
    private ConfigEventProducer configEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfflineRule createOfflineRule(OfflineRule offlineRule) {
        // 校验设备编码唯一性
        OfflineRule existing = getByDeviceCode(offlineRule.getDeviceCode());
        if (existing != null) {
            throw new RuntimeException("设备离线规则已存在: " + offlineRule.getDeviceCode());
        }

        // 设置默认值
        offlineRule.setCreatedAt(System.currentTimeMillis());
        offlineRule.setUpdatedAt(System.currentTimeMillis());
        if (offlineRule.getEnabled() == null) {
            offlineRule.setEnabled(true);
        }
        if (offlineRule.getTimeoutSeconds() == null) {
            offlineRule.setTimeoutSeconds(300); // 默认 5 分钟
        }

        // 保存到数据库
        save(offlineRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.OFFLINE_RULE, ConfigOpType.ADD, offlineRule);

        return offlineRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfflineRule updateOfflineRule(OfflineRule offlineRule) {
        // 更新数据库
        offlineRule.setUpdatedAt(System.currentTimeMillis());
        updateById(offlineRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.OFFLINE_RULE, ConfigOpType.UPDATE, offlineRule);

        return offlineRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOfflineRule(String deviceCode) {
        // 查询规则
        OfflineRule offlineRule = getByDeviceCode(deviceCode);
        if (offlineRule != null) {
            // 删除数据库记录
            removeById(offlineRule.getId());

            // 发送配置变更事件
            configEventProducer.sendConfigChangeEvent(ConfigChangeType.OFFLINE_RULE, ConfigOpType.DELETE, offlineRule);
        }
    }

    @Override
    public OfflineRule getByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<OfflineRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OfflineRule::getDeviceCode, deviceCode);
        return getOne(wrapper);
    }

    @Override
    public List<OfflineRule> getAllOfflineRules() {
        return list();
    }
}