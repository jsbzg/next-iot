package com.nextiot.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.config.mapper.ParseRuleMapper;
import com.nextiot.config.producer.ConfigEventProducer;
import com.nextiot.config.service.ParseRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 解析规则管理服务实现
 */
@Service
public class ParseRuleServiceImpl extends ServiceImpl<ParseRuleMapper, ParseRule> implements ParseRuleService {

    @Resource
    private ConfigEventProducer configEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseRule createParseRule(ParseRule parseRule) {
        // 获取当前网关类型的最新版本号
        Integer latestVersion = getLatestVersion(parseRule.getGatewayType());
        parseRule.setVersion(latestVersion + 1);

        // 保存到数据库
        parseRule.setCreatedAt(System.currentTimeMillis());
        parseRule.setUpdatedAt(System.currentTimeMillis());
        if (parseRule.getEnabled() == null) {
            parseRule.setEnabled(true);
        }
        save(parseRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.PARSE_RULE, ConfigOpType.ADD, parseRule);

        return parseRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseRule updateParseRule(ParseRule parseRule) {
        // 更新数据库
        parseRule.setUpdatedAt(System.currentTimeMillis());
        updateById(parseRule);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.PARSE_RULE, ConfigOpType.UPDATE, parseRule);

        return parseRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteParseRule(Long id) {
        // 查询规则
        ParseRule parseRule = getById(id);
        if (parseRule != null) {
            // 删除数据库记录
            removeById(id);

            // 发送配置变更事件
            configEventProducer.sendConfigChangeEvent(ConfigChangeType.PARSE_RULE, ConfigOpType.DELETE, parseRule);
        }
    }

    @Override
    public ParseRule getLatestByGatewayType(String gatewayType) {
        LambdaQueryWrapper<ParseRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseRule::getGatewayType, gatewayType)
                .eq(ParseRule::getEnabled, true)
                .orderByDesc(ParseRule::getVersion)
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    @Override
    public List<ParseRule> getAllParseRules() {
        return list();
    }

    /**
     * 获取当前网关类型的最新版本号
     */
    private Integer getLatestVersion(String gatewayType) {
        ParseRule latest = getLatestByGatewayType(gatewayType);
        return latest != null ? latest.getVersion() : 0;
    }
}