package com.nextiot.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nextiot.common.entity.ParseRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 解析规则 Mapper
 * 可替换点：当前使用 MySQL，后续可迁移 Redis / 配置中心缓存数据
 */
@Mapper
public interface ParseRuleMapper extends BaseMapper<ParseRule> {
}