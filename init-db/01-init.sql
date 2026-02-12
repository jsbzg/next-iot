-- IoT 数据中台数据库初始化脚本
-- 数据库版本: 1.0.0

-- 使用数据库
USE `next_iot`;

-- ----------------------------
-- 1. 物模型表 (thing_model)
-- ----------------------------
DROP TABLE IF EXISTS `thing_model`;
CREATE TABLE `thing_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `model_code` VARCHAR(64) NOT NULL COMMENT '模型编码（唯一）',
    `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物模型表';

-- ----------------------------
-- 2. 点位定义表 (thing_property)
-- ----------------------------
DROP TABLE IF EXISTS `thing_property`;
CREATE TABLE `thing_property` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `model_code` VARCHAR(64) NOT NULL COMMENT '所属物模型编码',
    `property_code` VARCHAR(64) NOT NULL COMMENT '点位编码',
    `property_name` VARCHAR(128) NOT NULL COMMENT '点位名称',
    `data_type` VARCHAR(16) NOT NULL COMMENT '数据类型：int/double/string/bool',
    `unit` VARCHAR(32) NULL COMMENT '单位',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    KEY `idx_model_code` (`model_code`),
    UNIQUE KEY `uk_property` (`model_code`, `property_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点位定义表';

-- ----------------------------
-- 3. 设备实例表 (thing_device) - 设备白名单主数据
-- ----------------------------
DROP TABLE IF EXISTS `thing_device`;
CREATE TABLE `thing_device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_code` VARCHAR(64) NOT NULL COMMENT '设备编码（唯一）',
    `model_code` VARCHAR(64) NOT NULL COMMENT '所属物模型编码',
    `gateway_type` VARCHAR(32) NOT NULL COMMENT '网关类型',
    `device_name` VARCHAR(128) NULL COMMENT '设备名称',
    `online` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '在线状态：0-离线 1-在线',
    `last_seen_ts` BIGINT NULL COMMENT '最后上报时间（时间戳）',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`device_code`),
    KEY `idx_model_code` (`model_code`),
    KEY `idx_gateway_type` (`gateway_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备实例表（设备白名单）';

-- ----------------------------
-- 4. 解析规则表 (parse_rule)
-- ----------------------------
DROP TABLE IF EXISTS `parse_rule`;
CREATE TABLE `parse_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `gateway_type` VARCHAR(32) NOT NULL COMMENT '网关类型',
    `protocol_type` VARCHAR(16) NOT NULL COMMENT '协议类型：mqtt/http/tcp',
    `match_expr` TEXT NULL COMMENT '匹配表达式（Aviator：判断是否命中该规则）',
    `parse_script` TEXT NULL COMMENT '解析脚本（Aviator：从原始报文提取字段）',
    `mapping_script` TEXT NULL COMMENT '映射脚本（Aviator：映射为标准格式）',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    KEY `idx_gateway_type` (`gateway_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='解析规则表';

-- ----------------------------
-- 5. 告警规则表 (alarm_rule)
-- ----------------------------
DROP TABLE IF EXISTS `alarm_rule`;
CREATE TABLE `alarm_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码',
    `device_code` VARCHAR(64) NULL COMMENT '设备编码（空表示按模型）',
    `property_code` VARCHAR(64) NOT NULL COMMENT '点位编码',
    `condition_expr` VARCHAR(512) NOT NULL COMMENT '条件表达式（Aviator：value > 80）',
    `trigger_type` VARCHAR(16) NOT NULL COMMENT '触发类型：CONTINUOUS_N / WINDOW',
    `trigger_n` INT NOT NULL DEFAULT 1 COMMENT '触发参数N值',
    `window_seconds` INT NULL COMMENT '窗口大小（秒，仅 WINDOW 类型）',
    `suppress_seconds` INT NOT NULL DEFAULT 60 COMMENT '流内抑制时间（秒）',
    `level` TINYINT NOT NULL DEFAULT 1 COMMENT '告警级别：0-提示 1-警告 2-严重 3-紧急',
    `description` VARCHAR(512) NULL COMMENT '告警描述',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`),
    KEY `idx_device_code` (`device_code`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则表';

-- ----------------------------
-- 6. 离线规则表 (offline_rule)
-- ----------------------------
DROP TABLE IF EXISTS `offline_rule`;
CREATE TABLE `offline_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_code` VARCHAR(64) NOT NULL COMMENT '设备编码',
    `timeout_seconds` INT NOT NULL COMMENT '超时时长（秒）',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`device_code`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线规则表';

-- ----------------------------
-- 7. 告警实例表 (alarm_instance) - 可替换为 Redis/ES
-- ----------------------------
DROP TABLE IF EXISTS `alarm_instance`;
CREATE TABLE `alarm_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '告警规则编码',
    `device_code` VARCHAR(64) NOT NULL COMMENT '设备编码',
    `status` VARCHAR(16) NOT NULL COMMENT '状态：ACTIVE / ACKED / RECOVERED',
    `first_trigger_time` BIGINT NOT NULL COMMENT '首次触发时间（时间戳）',
    `last_trigger_time` BIGINT NOT NULL COMMENT '最后触发时间（时间戳）',
    `ack_time` BIGINT NULL COMMENT '确认时间（时间戳）',
    `ack_user` VARCHAR(64) NULL COMMENT '确认人',
    `recover_time` BIGINT NULL COMMENT '恢复时间（时间戳）',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    `updated_at` BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间（时间戳）',
    PRIMARY KEY (`id`),
    KEY `idx_rule_device` (`rule_code`, `device_code`),
    KEY `idx_status` (`status`),
    KEY `idx_last_trigger` (`last_trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警实例表';

-- ----------------------------
-- 8. 时序数据表 (metric_data) - 可替换为 ClickHouse/TDengine
-- ----------------------------
DROP TABLE IF EXISTS `metric_data`;
CREATE TABLE `metric_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_code` VARCHAR(64) NOT NULL COMMENT '设备编码',
    `property_code` VARCHAR(64) NOT NULL COMMENT '点位编码',
    `value` DOUBLE NULL COMMENT '数值',
    `str_value` VARCHAR(512) NULL COMMENT '字符串值',
    `ts` BIGINT NOT NULL COMMENT '时间戳',
    `created_at` BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间（时间戳）',
    PRIMARY KEY (`id`),
    KEY `idx_device_property` (`device_code`, `property_code`),
    KEY `idx_ts` (`ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时序数据表';

-- ----------------------------
-- 插入测试数据
-- ----------------------------

-- 插入物模型示例
INSERT INTO `thing_model` (`model_code`, `model_name`, `created_at`, `updated_at`) VALUES
('temp_sensor', '温度传感器', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('pressure_sensor', '压力传感器', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 插入点位定义示例
INSERT INTO `thing_property` (`model_code`, `property_code`, `property_name`, `data_type`, `unit`, `created_at`, `updated_at`) VALUES
('temp_sensor', 'temperature', '温度', 'double', '℃', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('temp_sensor', 'humidity', '湿度', 'double', '%', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('pressure_sensor', 'pressure', '压力', 'double', 'Pa', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 插入设备示例（设备白名单）
INSERT INTO `thing_device` (`device_code`, `model_code`, `gateway_type`, `device_name`, `online`, `last_seen_ts`, `created_at`, `updated_at`) VALUES
('dev_001', 'temp_sensor', 'mqtt_gateway', '温度传感器-001', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('dev_002', 'temp_sensor', 'mqtt_gateway', '温度传感器-002', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('dev_003', 'pressure_sensor', 'http_gateway', '压力传感器-001', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 插入解析规则示例（MQTT JSON 格式）
INSERT INTO `parse_rule` (`gateway_type`, `protocol_type`, `match_expr`, `parse_script`, `mapping_script`, `version`, `enabled`, `created_at`, `updated_at`) VALUES
('mqtt_gateway', 'mqtt',
 'json.containsKey(\"deviceCode\") && json.containsKey(\"temperature\")',
 'let parsed = json; parsed',
 'let result = seq.map(\"deviceCode\", json.deviceCode, \"propertyCode\", \"temperature\", \"value\", json.temperature, \"ts\", now()); result',
 1, 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 插入告警规则示例
INSERT INTO `alarm_rule` (`rule_code`, `device_code`, `property_code`, `condition_expr`, `trigger_type`, `trigger_n`, `window_seconds`, `suppress_seconds`, `level`, `description`, `enabled`, `created_at`, `updated_at`) VALUES
('TEMP_HIGH_3', NULL, 'temperature', 'value > 80', 'CONTINUOUS_N', 3, NULL, 60, 2, '温度连续3次超过80℃', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 插入离线规则示例
INSERT INTO `offline_rule` (`device_code`, `timeout_seconds`, `enabled`, `created_at`, `updated_at`) VALUES
('dev_001', 300, 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('dev_002', 300, 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
('dev_003', 300, 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);