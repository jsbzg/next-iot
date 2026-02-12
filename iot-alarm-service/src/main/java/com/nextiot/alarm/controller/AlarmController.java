package com.nextiot.alarm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nextiot.alarm.service.AlarmService;
import com.nextiot.common.dto.Result;
import com.nextiot.common.entity.AlarmInstance;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警管理控制器
 */
@RestController
@RequestMapping("/api/alarm")
@CrossOrigin(origins = "*")
public class AlarmController {

    @Resource
    private AlarmService alarmService;

    /**
     * 查询所有告警
     */
    @GetMapping("/list")
    public Result<List<AlarmInstance>> list(@RequestParam(name = "status",required = false) String status) {
        LambdaQueryWrapper<AlarmInstance> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AlarmInstance::getStatus, status);
        }
        wrapper.orderByDesc(AlarmInstance::getLastTriggerTime);
        List<AlarmInstance> alarms = alarmService.list(wrapper);
        return Result.success(alarms);
    }

    /**
     * 查询单个告警详情
     */
    @GetMapping("/{id}")
    public Result<AlarmInstance> getById(@PathVariable Long id) {
        AlarmInstance alarm = alarmService.getById(id);
        if (alarm == null) {
            return Result.fail("告警不存在");
        }
        return Result.success(alarm);
    }

    /**
     * 人工确认告警
     */
    @PostMapping("/{id}/ack")
    public Result<Void> ack(@PathVariable Long id, @RequestParam String user) {
        try {
            alarmService.ackAlarm(id, user);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("确认告警失败：" + e.getMessage());
        }
    }

    /**
     * 恢复告警
     */
    @PostMapping("/recover")
    public Result<Void> recover(@RequestParam String ruleCode, @RequestParam String deviceCode) {
        try {
            alarmService.recoverAlarm(ruleCode, deviceCode);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("恢复告警失败：" + e.getMessage());
        }
    }
}