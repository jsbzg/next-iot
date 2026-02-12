# IoT 数据中台 Demo

基于 Flink + Kafka 的物联网数据中台 Demo，实现多网关/多协议/多报文格式接入、动态规则检测、流内抑制、离线检测等核心功能。

## 核心特性

### 1. 规则动态刷新
- 所有规则配置（解析规则、告警规则、离线规则）均可动态刷新
- 新增或修改规则时，**无需重启 Flink Job**
- 通过 Kafka `config-topic` 实时推送配置变更
- Flink 使用 Broadcast State 毫秒级生效

### 2. 规则类型

| 规则类型 | 说明 | 动态刷新 |
|---------|------|---------|
| 解析规则 | 使用 Aviator 表达式解析原始报文 | ✅ 支持 |
| 告警规则 | 支持连续N次/时间窗口两种触发类型 | ✅ 支持 |
| 离线规则 | 设备离线超时检测 | ✅ 支持 |
| 设备白名单 | 设备合法性校验 | ✅ 支持 |

### 3. 规则管理 API
提供完整的 RESTful API，支持规则的增删改查操作，所有变更自动同步到 Flink。

## 项目架构

```
[ 网关设备 ]
     |
     v
[ Kafka: raw-topic ]
     |
     v
[ Flink Job ] (核心处理)
  1. 动态解析（ParseRule, Aviator）
  2. 设备合法性校验（ThingDevice 广播状态）
  3. 动态检测规则（AlarmRule, Aviator）
     - 连续N次 / 窗口
  4. 流内抑制（State 防刷屏）
  5. 离线检测（OfflineRule + 定时器）
     |
     +--> [ Kafka: metric-topic ]（可选）
     |
     +--> [ Kafka: alarm-event-topic ]
             |
             v
      [ Alarm Service ]
       - 业务抑制
       - 生命周期管理
       - 通知编排

[ Config Service ]
   - 管理：物模型 / 设备 / 解析规则 / 告警规则 / 离线规则
   - 写 MySQL
   - 发送 ConfigChangeEvent -> Kafka: config-topic
                   |
                   v
             [ Flink Broadcast State ]
             动态刷新所有规则 + 设备主数据
```

## 技术栈

- 后端
  - JDK 17
  - Spring Boot 3.2.0
  - Flink 1.18.1
  - MySQL 8.0
  - Kafka 3.6.0
  - AviatorScript（表达式引擎）

## 模块说明

| 模块 | 说明 |
|------|------|
| iot-common | 公共模型、工具类、API 响应封装 |
| iot-config-service | 配置管理服务和 REST API（设备、物模型、规则管理） |
| iot-alarm-service | 告警服务（业务抑制、生命周期管理、通知编排） |
| iot-flink-job | Flink 实时流处理 Job（解析、检测、抑制、离线检测） |
| iot-frontend | 前端管理页面（设备、物模型、规则、告警管理） |

## 快速开始

### 1. 启动 Docker 环境

```bash
docker-compose up -d
```

### 2. 等待 MySQL 初始化

等待约 30 秒，确保 MySQL 初始化完成。

### 3. 启动配置服务

```bash
cd iot-config-service
mvn clean package
java -jar target/iot-config-service-1.0.0.jar
```

### 4. 启动告警服务

```bash
cd iot-alarm-service
mvn clean package
java -jar target/iot-alarm-service-1.0.0.jar
```

### 5. 启动 Flink Job

```bash
cd iot-flink-job
mvn clean package
# 将生成的 jar 包复制到 flink-job 目录
cp target/iot-flink-job-1.0.0.jar ../flink-job/
# 通过 Flink Web UI (http://localhost:8081) 提交作业
```

### 6. 验证系统运行

- 访问 Flink Web UI: http://localhost:8081
- 访问 Kafka UI: http://localhost:8080
- 查看告警日志

## 规则管理 API

### Config Service (端口 8085)

#### 设备管理
- `GET /api/device/list` - 查询所有设备
- `GET /api/device/{deviceCode}` - 查询单个设备
- `POST /api/device/create` - 创建设备
- `POST /api/device/update` - 更新设备
- `DELETE /api/device/{deviceCode}` - 删除设备

#### 物模型管理
- `GET /api/model/list` - 查询所有物模型
- `POST /api/model/create` - 创建物模型
- `POST /api/model/update` - 更新物模型
- `DELETE /api/model/{modelCode}` - 删除物模型
- `GET /api/model/{modelCode}/properties` - 查询物模型点位
- `POST /api/model/property/create` - 创建点位
- `POST /api/model/property/update` - 更新点位
- `DELETE /api/model/property/{id}` - 删除点位

#### 解析规则管理
- `GET /api/parse-rule/list` - 查询所有解析规则
- `GET /api/parse-rule/gateway/{gatewayType}` - 根据网关类型查询解析规则
- `POST /api/parse-rule/create` - 创建解析规则
- `POST /api/parse-rule/update` - 更新解析规则
- `DELETE /api/parse-rule/{id}` - 删除解析规则

#### 告警规则管理
- `GET /api/alarm-rule/list` - 查询所有告警规则
- `GET /api/alarm-rule/rule/{ruleCode}` - 根据规则编码查询告警规则
- `POST /api/alarm-rule/create` - 创建告警规则
- `POST /api/alarm-rule/update` - 更新告警规则
- `DELETE /api/alarm-rule/{ruleCode}` - 删除告警规则

#### 离线规则管理
- `GET /api/offline-rule/list` - 查询所有离线规则
- `GET /api/offline-rule/device/{deviceCode}` - 根据设备编码查询离线规则
- `POST /api/offline-rule/create` - 创建离线规则
- `POST /api/offline-rule/update` - 更新离线规则
- `DELETE /api/offline-rule/{deviceCode}` - 删除离线规则

### Alarm Service (端口 8086)

- `GET /api/alarm/list` - 查询所有告警
- `GET /api/alarm/{id}` - 查询告警详情
- `POST /api/alarm/{id}/ack` - 确认告警

## 可替换点说明

### 1. 规则状态缓存

**当前**：MySQL + Flink State

**未来**：MySQL + Redis + 配置中心

### 2. 时序数据存储

**当前**：MySQL（仅日志）

**未来**：ClickHouse / TDengine / InfluxDB

### 3. 告警实例状态

**当前**：MySQL

**未来**：Redis + ES（高并发场景）

## 设计亮点

### 1. Flink 为智能水龙头

在 Flink 中完成：
- 报文解析
- 设备合法性校验（第一道业务闸门）
- 告警检测
- 流内抑制（防刷屏）
- 离线检测

目的：不让脏数据、不合规设备、不受控告警洪水进入下游。

### 2. Alarm Service 为用水管家

只做：
- 业务策略抑制（维护窗口、静默策略）
- 生命周期管理
- 通知编排

### 3. 动态规则刷新

使用 Broadcast State 实现规则动态刷新，新增网关类型或设备时：
- 只需在页面配置解析规则/设备信息/告警规则
- **无需重启 Flink Job**

### 4. 设备合法性过滤

位置：解析完成之后、检测之前

目的：
- 防止污染 Flink State
- 防止污染指标流
- 防止污染告警流

## 测试数据

Flink Job 内置模拟数据源，会持续生成以下设备数据：
- dev_001: 模拟温度数据（正常: 20-30°C，异常: > 80°C）
- dev_002: 模拟温度数据
- dev_003: 模拟温度数据

告警规则：温度连续 3 次超过 80°C

## 扩展开发

### 添加新的网关类型

1. 在 `parse_rule` 表中添加解析规则配置
2. 通过 Config Service API 创建规则
3. Flink 会自动通过 Broadcast State 获取新规则，无需重启 Job

### 添加新的告警规则

1. 在 `alarm_rule` 表中添加告警规则配置
2. 通过 Config Service API 创建规则
3. Flink 会自动应用新规则，无需重启 Job

## 许可证

MIT License