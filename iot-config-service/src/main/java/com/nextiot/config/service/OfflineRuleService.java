package com.nextiot.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.OfflineRule;

import java.util.List;

/**
 * 离线规则管理服务接口
 */
public interface OfflineRuleService extends IService<OfflineRule> {

    /**
     * 创建离线规则并发送配置变更事件
     */
    OfflineRule createOfflineRule(OfflineRule offlineRule);

    /**
     * 更新离线规则并发送配置变更事件
     */
    OfflineRule updateOfflineRule(OfflineRule offlineRule);

    /**
     * 删除离线规则并发送配置变更事件
     */
    void deleteOfflineRule(String deviceCode);

    /**
     * 根据设备编码获取离线规则
     */
    OfflineRule getByDeviceCode(String deviceCode);

    /**
     * 获取所有离线规则
     */
    List<OfflineRule> getAllOfflineRules();
}