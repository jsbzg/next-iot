-- IoT 数据中台数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS next_iot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE next_iot;

-- ===== 物模型表 =====
CREATE TABLE IF NOT EXISTS thing_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    model_code VARCHAR(50) NOT NULL UNIQUE COMMENT '模型编码',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物模型表';

-- ===== 点位定义表 =====
CREATE TABLE IF NOT EXISTS thing_property (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    model_code VARCHAR(50) NOT NULL COMMENT '所属物模型编码',
    property_code VARCHAR(50) NOT NULL COMMENT '点位编码',
    property_name VARCHAR(100) NOT NULL COMMENT '点位名称',
    data_type VARCHAR(20) NOT NULL COMMENT '数据类型：int/double/string/bool',
    unit VARCHAR(20) COMMENT '单位',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    UNIQUE KEY uk_model_property (model_code, property_code),
    INDEX idx_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点位定义表';

-- ===== 设备实例表（设备白名单主数据）=====
CREATE TABLE IF NOT EXISTS thing_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_code VARCHAR(50) NOT NULL UNIQUE COMMENT '设备编码',
    model_code VARCHAR(50) NOT NULL COMMENT '所属物模型编码',
    gateway_type VARCHAR(50) NOT NULL COMMENT '网关类型',
    device_name VARCHAR(100) COMMENT '设备名称',
    online TINYINT(1) DEFAULT 1 COMMENT '在线状态：0-离线 1-在线',
    last_seen_ts BIGINT DEFAULT 0 COMMENT '最后上报时间戳',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_device_code (device_code),
    INDEX idx_model_code (model_code),
    INDEX idx_gateway_type (gateway_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备实例表';

-- ===== 解析规则表 =====--
CREATE TABLE IF NOT EXISTS parse_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    gateway_type VARCHAR(50) NOT NULL COMMENT '网关类型',
    protocol_type VARCHAR(20) NOT NULL COMMENT '协议类型：mqtt/http/tcp',
    match_expr TEXT COMMENT '匹配表达式（Aviator 表达式）',
    parse_script TEXT COMMENT '解析脚本（Aviator 脚本）',
    mapping_script TEXT COMMENT '映射脚本（Aviator 脚本，可选）',
    parse_mode VARCHAR(20) DEFAULT 'STRING' COMMENT '解析模式：STRING（新）/ LEGACY（旧）',
    key_extractor_script TEXT COMMENT 'Key 提取脚本（可选，用于优化 keyBy）',
    parser_type VARCHAR(20) COMMENT '解析器类型：JSON/CSV/PIPE/FIXED_WIDTH/HEX/CUSTOM',
    sample_input TEXT COMMENT '示例输入（用于规则调试）',
    validation_regex VARCHAR(500) COMMENT '验证正则表达式（可选）',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_gateway_type (gateway_type),
    INDEX idx_version (version),
    INDEX idx_parser_type (parser_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析规则表';

-- ===== 告警规则表 =====
CREATE TABLE IF NOT EXISTS alarm_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    rule_code VARCHAR(50) NOT NULL UNIQUE COMMENT '规则编码',
    device_code VARCHAR(50) COMMENT '设备编码（为空表示按模型）',
    property_code VARCHAR(50) NOT NULL COMMENT '点位编码',
    condition_expr TEXT NOT NULL COMMENT '条件表达式（Aviator 表达式）',
    trigger_type VARCHAR(20) NOT NULL COMMENT '触发类型：CONTINUOUS_N/WINDOW',
    trigger_n INT NOT NULL COMMENT '触发次数',
    window_seconds INT COMMENT '窗口大小（秒，WINDOW 类型有效）',
    suppress_seconds INT NOT NULL DEFAULT 60 COMMENT '流内抑制时间（秒）',
    level INT NOT NULL DEFAULT 1 COMMENT '告警级别：0-提示 1-警告 2-严重 3-紧急',
    description VARCHAR(255) COMMENT '告警描述',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_rule_code (rule_code),
    INDEX idx_device_code (device_code),
    INDEX idx_property_code (property_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- ===== 离线规则表 =====
CREATE TABLE IF NOT EXISTS offline_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_code VARCHAR(50) NOT NULL UNIQUE COMMENT '设备编码',
    timeout_seconds INT NOT NULL DEFAULT 300 COMMENT '超时时长（秒）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_device_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线规则表';

-- ===== 告警实例表 =====
CREATE TABLE IF NOT EXISTS alarm_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    rule_code VARCHAR(50) NOT NULL COMMENT '规则编码',
    device_code VARCHAR(50) NOT NULL COMMENT '设备编码',
    property_code VARCHAR(50) COMMENT '点位编码',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ACKED/RECOVERED',
    level INT NOT NULL COMMENT '告警级别',
    description VARCHAR(255) COMMENT '告警描述',
    first_trigger_time BIGINT NOT NULL COMMENT '首次触发时间',
    last_trigger_time BIGINT NOT NULL COMMENT '最后触发时间',
    ack_time BIGINT COMMENT '确认时间',
    recovered_time BIGINT COMMENT '恢复时间',
    ack_user VARCHAR(50) COMMENT '确认用户',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间',
    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间',
    INDEX idx_rule_code (rule_code),
    INDEX idx_device_code (device_code),
    INDEX idx_status (status),
    INDEX idx_last_trigger (last_trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警实例表';

-- ===== 设备数据表 =====
CREATE TABLE IF NOT EXISTS device_metric_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_code VARCHAR(50) NOT NULL COMMENT '设备编码',
    property_code VARCHAR(50) NOT NULL COMMENT '点位编码',
    value DOUBLE COMMENT '数值型值',
    str_value VARCHAR(255) COMMENT '字符串值',
    ts BIGINT NOT NULL COMMENT '上报时间戳',
    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '入库时间',
    INDEX idx_device_ts (device_code, ts),
    INDEX idx_ts (ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备数据表（时序数据）';

-- ===== 初始化示例数据 =====

-- 物模型示例：温湿度传感器
INSERT INTO thing_model (model_code, model_name) VALUES
('SENSOR_TPH', '温湿度传感器');

-- 点位定义示例
INSERT INTO thing_property (model_code, property_code, property_name, data_type, unit) VALUES
('SENSOR_TPH', 'temperature', '温度', 'double', '°C'),
('SENSOR_TPH', 'humidity', '湿度', 'double', '%'),
('SENSOR_TPH', 'pressure', '气压', 'double', 'hPa');

-- 设备实例示例
INSERT INTO thing_device (device_code, model_code, gateway_type, device_name, online, last_seen_ts) VALUES
('dev_001', 'SENSOR_TPH', 'MQTT', '1号车间温湿度传感器', 1, UNIX_TIMESTAMP() * 1000),
('dev_002', 'SENSOR_TPH', 'MQTT', '2号车间温湿度传感器', 1, UNIX_TIMESTAMP() * 1000),
('dev_003', 'SENSOR_TPH', 'HTTP', '3号车间温湿度传感器', 1, UNIX_TIMESTAMP() * 1000),
('dev_004', 'SENSOR_TPH', 'CSV', 'CSV格式设备', 1, UNIX_TIMESTAMP() * 1000),
('dev_005', 'SENSOR_TPH', 'PIPE_DELIMITED', '管道符分隔设备', 1, UNIX_TIMESTAMP() * 1000);

-- 解析规则示例：支持多种格式

-- 示例1: MQTT JSON 格式（单设备单属性）- 输入: {"gatewayType":"MQTT","deviceCode":"dev_001","temperature":25.5,"ts":1690000000}
INSERT INTO parse_rule (gateway_type, protocol_type, parser_type, match_expr, parse_script, mapping_script, sample_input, parse_mode, version, enabled) VALUES
('MQTT', 'mqtt', 'JSON', 'deviceCode != nil',
'let m = seq.map(); m.deviceCode = rawMap.deviceCode; m.propertyCode = ''temperature''; m.value = rawMap.temperature; m.ts = rawMap.ts; return m;',
NULL,
'{"gatewayType":"MQTT","deviceCode":"dev_001","temperature":25.5,"ts":1690000000}',
'STRING',
1, 1);

-- 示例2: CSV 格式 - 输入: dev_004,1771913823000,26.5,55.2,1013.25
INSERT INTO parse_rule (gateway_type, protocol_type, parser_type, match_expr, parse_script, mapping_script, sample_input, validation_regex, parse_mode, version, enabled) VALUES
('CSV', 'tcp', 'CSV', 'length(split(rawMessage, '','')) >= 5',
'let fields = split(rawMessage, '',''); let m = seq.map(); m.deviceCode = fields[0]; m.ts = long(fields[1]); m.value = double(fields[2]); return m;',
'let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = ''temperature''; out.value = parsed.value; out.ts = parsed.ts; return out;',
'dev_004,1771913823000,26.5,55.2,1013.25',
'^[A-Za-z0-9_]+,\\d+,\\d+\\.\\d+',
'STRING',
1, 1);

-- 示例3: 管道符分隔格式 - 输入: dev_005|1771913823|26.7
INSERT INTO parse_rule (gateway_type, protocol_type, parser_type, match_expr, parse_script, mapping_script, sample_input, validation_regex, parse_mode, version, enabled) VALUES
('PIPE_DELIMITED', 'tcp', 'PIPE', 'length(split(rawMessage, ''\\|'')) >= 3',
'let parts = split(rawMessage, ''\\|''); let m = seq.map(); m.deviceCode = parts[0]; m.ts = long(parts[1]) * 1000; m.value = double(parts[2]); return m;',
'let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = ''temperature''; out.value = parsed.value; out.ts = parsed.ts; return out;',
'dev_005|1771913823|26.7',
'^[A-Za-z0-9_]+\\|\\d+\\|\\d+\\.\\d+',
'STRING',
1, 1);

-- 示例4: 固定长度格式 - 输入: dev003169000000026.50
INSERT INTO parse_rule (gateway_type, protocol_type, parser_type, match_expr, parse_script, mapping_script, sample_input, validation_regex, parse_mode, version, enabled) VALUES
('FIXED_WIDTH', 'tcp', 'FIXED_WIDTH', 'length(rawMessage) >= 24',
'let m = seq.map(); m.deviceCode = substring(rawMessage, 0, 7); m.ts = long(substring(rawMessage, 7, 20)); m.value = double(substring(rawMessage, 20, 24)); return m;',
'let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = ''temperature''; out.value = parsed.value; out.ts = parsed.ts; return out;',
'dev003169000000026.50',
'^[a-z0-9]+\\d{13}\\d+\\.\\d+',
'STRING',
1, 1);

-- 告警规则示例
INSERT INTO alarm_rule (rule_code, device_code, property_code, condition_expr, trigger_type, trigger_n, window_seconds, suppress_seconds, level, description, enabled) VALUES
('TEMP_HIGH_3', 'dev_001', 'temperature', 'value > 80', 'CONTINUOUS_N', 3, NULL, 60, 2, '温度连续3次超过80°C', 1),
('TEMP_WINDOW_5', NULL, 'temperature', 'value > 85', 'WINDOW', 5, 60, 60, 3, '温度60秒内超过85°C达5次', 1),
('TEMP_HIGH_ANY', 'dev_002', 'temperature', 'value > 90', 'CONTINUOUS_N', 1, NULL, 30, 3, '温度瞬间超过90°C', 1);

-- 离线规则示例
INSERT INTO offline_rule (device_code, timeout_seconds, enabled) VALUES
('dev_001', 300, 1),
('dev_002', 300, 1),
('dev_003', 600, 1);

-- 初始化完成
SELECT '数据库初始化完成' AS status;