package com.nextiot.config.init;

import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.entity.ParseRule;
import com.nextiot.common.entity.ThingDevice;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.config.mapper.AlarmRuleMapper;
import com.nextiot.config.mapper.OfflineRuleMapper;
import com.nextiot.config.mapper.ParseRuleMapper;
import com.nextiot.config.mapper.ThingDeviceMapper;
import com.nextiot.config.producer.ConfigEventProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置初始化加载器
 * 服务启动时加载所有配置并发送到 Kafka，供 Flink 实时同步配置
 */
@Component
public class ConfigInitializer {

    private static final Logger log = LoggerFactory.getLogger(ConfigInitializer.class);

    @Resource
    private ConfigEventProducer configEventProducer;

    @Resource
    private ThingDeviceMapper thingDeviceMapper;

    @Resource
    private AlarmRuleMapper alarmRuleMapper;

    @Resource
    private ParseRuleMapper parseRuleMapper;

    @Resource
    private OfflineRuleMapper offlineRuleMapper;

    /**
     * 延迟加载时间（秒），等待 Kafka 初始化完成
     */
    private static final int DELAY_SECONDS = 5;

    @PostConstruct
    public void init() {
        // 延迟加载，确保各组件已初始化完成
        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(DELAY_SECONDS);
                loadAllConfigs();
            } catch (InterruptedException e) {
                log.error("配置初始化线程被中断", e);
                Thread.currentThread().interrupt();
            }
        }, "ConfigInitializer-Thread").start();
    }

    /**
     * 加载所有配置并发送到 Kafka
     */
    private void loadAllConfigs() {
        log.info("========================================");
        log.info("开始加载所有配置到 Kafka...");
        log.info("========================================");

        try {
            // 加载设备
            int deviceCount = loadDevices();

            // 加载解析规则
            int parseRuleCount = loadParseRules();

            // 加载告警规则
            int alarmRuleCount = loadAlarmRules();

            // 加载离线规则
            int offlineRuleCount = loadOfflineRules();

            log.info("========================================");
            log.info("配置加载完成 - 设备: {}, 解析规则: {}, 告警规则: {}, 离线规则: {}", deviceCount, parseRuleCount, alarmRuleCount, offlineRuleCount);
            log.info("========================================");
        } catch (Exception e) {
            log.error("配置加载失败", e);
        }
    }

    /**
     * 加载所有设备
     */
    private int loadDevices() {
        List<ThingDevice> devices = thingDeviceMapper.selectList(null);
        AtomicInteger count = new AtomicInteger(0);

        for (ThingDevice device : devices) {
            try {
                configEventProducer.sendConfigChangeEvent(
                    ConfigChangeType.DEVICE,
                    ConfigOpType.ADD,
                    device
                );
                count.incrementAndGet();
                log.debug("发送设备: id={}, deviceCode={}, modelCode={}",
                    device.getId(), device.getDeviceCode(), device.getModelCode());
            } catch (Exception e) {
                log.error("发送设备失败: id={}, error={}", device.getId(), e.getMessage());
            }
        }

        log.info("加载设备完成: 共 {} 条", count.get());
        return count.get();
    }

    /**
     * 加载所有解析规则
     */
    private int loadParseRules() {
        List<ParseRule> rules = parseRuleMapper.selectList(null);
        AtomicInteger count = new AtomicInteger(0);

        for (ParseRule rule : rules) {
            try {
                configEventProducer.sendConfigChangeEvent(
                    ConfigChangeType.PARSE_RULE,
                    ConfigOpType.ADD,
                    rule
                );
                count.incrementAndGet();
                log.debug("发送解析规则: id={}, gatewayType={}, protocolType={}",
                    rule.getId(), rule.getGatewayType(), rule.getProtocolType());
            } catch (Exception e) {
                log.error("发送解析规则失败: id={}, error={}", rule.getId(), e.getMessage());
            }
        }

        log.info("加载解析规则完成: 共 {} 条", count.get());
        return count.get();
    }

    /**
     * 加载所有告警规则
     */
    private int loadAlarmRules() {
        List<AlarmRule> rules = alarmRuleMapper.selectList(null);
        AtomicInteger count = new AtomicInteger(0);

        for (AlarmRule rule : rules) {
            try {
                configEventProducer.sendConfigChangeEvent(
                    ConfigChangeType.ALARM_RULE,
                    ConfigOpType.ADD,
                    rule
                );
                count.incrementAndGet();
                log.debug("发送告警规则: id={}, ruleCode={}, deviceCode={}, propertyCode={}",
                    rule.getId(), rule.getRuleCode(), rule.getDeviceCode(), rule.getPropertyCode());
            } catch (Exception e) {
                log.error("发送告警规则失败: id={}, error={}", rule.getId(), e.getMessage());
            }
        }

        log.info("加载告警规则完成: 共 {} 条", count.get());
        return count.get();
    }

    /**
     * 加载所有离线规则
     */
    private int loadOfflineRules() {
        List<OfflineRule> rules = offlineRuleMapper.selectList(null);
        AtomicInteger count = new AtomicInteger(0);

        for (OfflineRule rule : rules) {
            try {
                configEventProducer.sendConfigChangeEvent(
                    ConfigChangeType.OFFLINE_RULE,
                    ConfigOpType.ADD,
                    rule
                );
                count.incrementAndGet();
                log.debug("发送离线规则: id={}, deviceCode={}, timeoutSeconds={}",
                    rule.getId(), rule.getDeviceCode(), rule.getTimeoutSeconds());
            } catch (Exception e) {
                log.error("发送离线规则失败: id={}, error={}", rule.getId(), e.getMessage());
            }
        }

        log.info("加载离线规则完成: 共 {} 条", count.get());
        return count.get();
    }
}