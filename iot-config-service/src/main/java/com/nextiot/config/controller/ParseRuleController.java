package com.nextiot.config.controller;

import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.dto.Result;
import com.nextiot.config.service.ParseRuleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 解析规则管理控制器
 */
@RestController
@RequestMapping("/api/parse-rule")
@CrossOrigin(origins = "*")
public class ParseRuleController {

    @Resource
    private ParseRuleService parseRuleService;

    /**
     * 查询所有解析规则
     */
    @GetMapping("/list")
    public Result<List<ParseRule>> list() {
        List<ParseRule> rules = parseRuleService.getAllParseRules();
        return Result.success(rules);
    }

    /**
     * 根据网关类型查询解析规则
     */
    @GetMapping("/gateway/{gatewayType}")
    public Result<ParseRule> getByGatewayType(@PathVariable String gatewayType) {
        ParseRule rule = parseRuleService.getLatestByGatewayType(gatewayType);
        if (rule == null) {
            return Result.fail("未找到该网关类型的解析规则");
        }
        return Result.success(rule);
    }

    /**
     * 创建解析规则
     */
    @PostMapping("/create")
    public Result<ParseRule> create(@RequestBody ParseRule rule) {
        try {
            ParseRule created = parseRuleService.createParseRule(rule);
            return Result.success(created);
        } catch (Exception e) {
            return Result.fail("创建解析规则失败：" + e.getMessage());
        }
    }

    /**
     * 更新解析规则
     */
    @PostMapping("/update")
    public Result<ParseRule> update(@RequestBody ParseRule rule) {
        try {
            ParseRule updated = parseRuleService.updateParseRule(rule);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新解析规则失败：" + e.getMessage());
        }
    }

    /**
     * 删除解析规则
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            parseRuleService.deleteParseRule(id);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除解析规则失败：" + e.getMessage());
        }
    }
}