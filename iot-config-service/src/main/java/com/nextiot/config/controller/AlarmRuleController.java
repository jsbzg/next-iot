package com.nextiot.config.controller;

import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.dto.Result;
import com.nextiot.config.service.AlarmRuleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则管理控制器
 */
@RestController
@RequestMapping("/api/alarm-rule")
@CrossOrigin(origins = "*")
public class AlarmRuleController {

    @Resource
    private AlarmRuleService alarmRuleService;

    /**
     * 查询所有告警规则
     */
    @GetMapping("/list")
    public Result<List<AlarmRule>> list() {
        List<AlarmRule> rules = alarmRuleService.getAllAlarmRules();
        return Result.success(rules);
    }

    /**
     * 根据规则编码查询告警规则
     */
    @GetMapping("/rule/{ruleCode}")
    public Result<AlarmRule> getByRuleCode(@PathVariable String ruleCode) {
        AlarmRule rule = alarmRuleService.getByRuleCode(ruleCode);
        if (rule == null) {
            return Result.fail("未找到该告警规则");
        }
        return Result.success(rule);
    }

    /**
     * 创建告警规则
     */
    @PostMapping("/create")
    public Result<AlarmRule> create(@RequestBody AlarmRule rule) {
        try {
            AlarmRule created = alarmRuleService.createAlarmRule(rule);
            return Result.success(created);
        } catch (Exception e) {
            return Result.fail("创建告警规则失败：" + e.getMessage());
        }
    }

    /**
     * 更新告警规则
     */
    @PostMapping("/update")
    public Result<AlarmRule> update(@RequestBody AlarmRule rule) {
        try {
            AlarmRule updated = alarmRuleService.updateAlarmRule(rule);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新告警规则失败：" + e.getMessage());
        }
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/{ruleCode}")
    public Result<Void> delete(@PathVariable String ruleCode) {
        try {
            alarmRuleService.deleteAlarmRule(ruleCode);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除告警规则失败：" + e.getMessage());
        }
    }
}