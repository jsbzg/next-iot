package com.nextiot.config.controller;

import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.dto.Result;
import com.nextiot.config.service.OfflineRuleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 离线规则管理控制器
 */
@RestController
@RequestMapping("/api/offline-rule")
@CrossOrigin(origins = "*")
public class OfflineRuleController {

    @Resource
    private OfflineRuleService offlineRuleService;

    /**
     * 查询所有离线规则
     */
    @GetMapping("/list")
    public Result<List<OfflineRule>> list() {
        List<OfflineRule> rules = offlineRuleService.getAllOfflineRules();
        return Result.success(rules);
    }

    /**
     * 根据设备编码查询离线规则
     */
    @GetMapping("/device/{deviceCode}")
    public Result<OfflineRule> getByDeviceCode(@PathVariable String deviceCode) {
        OfflineRule rule = offlineRuleService.getByDeviceCode(deviceCode);
        if (rule == null) {
            return Result.fail("未找到该设备的离线规则");
        }
        return Result.success(rule);
    }

    /**
     * 创建离线规则
     */
    @PostMapping("/create")
    public Result<OfflineRule> create(@RequestBody OfflineRule rule) {
        try {
            OfflineRule created = offlineRuleService.createOfflineRule(rule);
            return Result.success(created);
        } catch (Exception e) {
            return Result.fail("创建离线规则失败：" + e.getMessage());
        }
    }

    /**
     * 更新离线规则
     */
    @PostMapping("/update")
    public Result<OfflineRule> update(@RequestBody OfflineRule rule) {
        try {
            OfflineRule updated = offlineRuleService.updateOfflineRule(rule);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新离线规则失败：" + e.getMessage());
        }
    }

    /**
     * 删除离线规则
     */
    @DeleteMapping("/{deviceCode}")
    public Result<Void> delete(@PathVariable String deviceCode) {
        try {
            offlineRuleService.deleteOfflineRule(deviceCode);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除离线规则失败：" + e.getMessage());
        }
    }
}