package com.nextiot.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.ParseRule;

import java.util.List;

/**
 * 解析规则管理服务接口
 */
public interface ParseRuleService extends IService<ParseRule> {

    /**
     * 创建解析规则并发送配置变更事件
     */
    ParseRule createParseRule(ParseRule parseRule);

    /**
     * 更新解析规则并发送配置变更事件
     */
    ParseRule updateParseRule(ParseRule parseRule);

    /**
     * 删除解析规则并发送配置变更事件
     */
    void deleteParseRule(Long id);

    /**
     * 根据网关类型获取最新版本的解析规则
     */
    ParseRule getLatestByGatewayType(String gatewayType);

    /**
     * 获取所有解析规则
     */
    List<ParseRule> getAllParseRules();
}