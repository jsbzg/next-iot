# 补充规则管理功能说明

## 规则管理 API 总结

### 1. 解析规则管理 (ParseRule)

**Service**: `ParseRuleService`

**Controller**: `ParseRuleController`

**API 端点**:
- `GET /api/parse-rule/list` - 查询所有解析规则
- `GET /api/parse-rule/gateway/{gatewayType}` - 根据网关类型获取最新版本规则
- `POST /api/parse-rule/create` - 创建解析规则（自动递增版本号）
- `POST /api/parse-rule/update` - 更新解析规则
- `DELETE /api/parse-rule/{id}` - 删除解析规则

**特点**:
- 采用版本管理机制，创建新规则时自动递增版本号
- 支持多网关类型（MQTT、HTTP、TCP 等）
- 支持多协议类型
- 使用 Aviator 表达式引擎进行报文匹配和解析
- 创建/更新/删除操作均会发送配置变更事件到 Kafka

### 2. 告警规则管理 (AlarmRule)

**Service**: `AlarmRuleService`

**Controller**: `AlarmRuleController`

**API 端点**:
- `GET /api/alarm-rule/list` - 查询所有告警规则
- `GET /api/alarm-rule/rule/{ruleCode}` - 根据规则编码查询
- `POST /api/alarm-rule/create` - 创建告警规则
- `POST /api/alarm-rule/update` - 更新告警规则
- `DELETE /api/alarm-rule/{ruleCode}` - 删除告警规则

**特点**:
- 支持按设备配置或按物模型配置
- 触发类型支持：连续 N 次（CONTINUOUS_N）和时间窗口（WINDOW）
- 内置流内抑制时间配置
- 支持多级别告警（INFO、WARNING、CRITICAL、EMERGENCY）
- 条件表达式使用 Aviator 引擎
- 创建/更新/删除操作均会发送配置变更事件到 Kafka

### 3. 离线规则管理 (OfflineRule)

**Service**: `OfflineRuleService`

**Controller**: `OfflineRuleController`

**API 端点**:
- `GET /api/offline-rule/list` - 查询所有离线规则
- `GET /api/offline-rule/device/{deviceCode}` - 根据设备编码查询
- `POST /api/offline-rule/create` - 创建离线规则
- `POST /api/offline-rule/update` - 更新离线规则
- `DELETE /api/offline-rule/{deviceCode}` - 删除离线规则

**特点**:
- 按设备编码配置离线检测规则
- 可设置离线超时时长（秒）
- 创建/更新/删除操作均会发送配置变更事件到 Kafka

### 4. 物模型和点位管理 (ThingModel & ThingProperty)

**Controller**: `ThingModelController`

**API 端点 - 物模型**:
- `GET /api/model/list` - 查询所有物模型
- `POST /api/model/create` - 创建物模型
- `POST /api/model/update` - 更新物模型
- `DELETE /api/model/{modelCode}` - 删除物模型

**API 端点 - 点位**:
- `GET /api/model/{modelCode}/properties` - 根据物模型查询点位列表
- `POST /api/model/property/create` - 创建点位
- `POST /api/model/property/update` - 更新点位
- `DELETE /api/model/property/{id}` - 删除点位

## 配置变更事件机制

### 事件格式
```json
{
  "type": "PARSE_RULE | ALARM_RULE | OFFLINE_RULE | DEVICE",
  "op": "ADD | UPDATE | DELETE",
  "payload": { ... },
  "timestamp": 1690000000000
}
```

### 事件流程
1. 用户通过 API 操作规则
2. Service 层先持久化到 MySQL
3. 数据保存成功后，通过 `ConfigEventProducer` 发送事件到 Kafka `config-topic`
4. Flink Job 的 Broadcast Stream 消费事件
5. Flink 更新 Broadcast State，实时生效

### 动态刷新优势
- **无需重启 Flink Job**
- 毫秒级生效
- 支持版本回滚（解析规则）
- 事务安全（MySQL 写入成功后才发送事件）

## 前端规则管理功能

### 功能列表
1. 设备管理：新增、删除设备
2. 物模型管理：查看物模型列表
3. 解析规则管理：查看、删除解析规则
4. 告警规则管理：新增、删除告警规则，配置触发参数
5. 离线规则管理：新增、删除离线规则
6. 告警列表：查看告警、确认告警

### 模态框表单

#### 告警规则表单包含字段：
- 规则编码（唯一）
- 设备编码（可选，留空表示按模型配置）
- 点位编码（如：temperature）
- 条件表达式（Aviator 表达式，如：value > 80）
- 触发类型（连续N次 / 时间窗口）
- 触发参数 N 值
- 窗口大小（秒，仅 WINDOW 类型）
- 流内抑制时间（秒）
- 告警级别
- 描述
- 是否启用

## 代码文件清单

### Config Service
```
iot-config-service/src/main/java/com/nextiot/config/
├── ConfigServiceApplication.java
├── controller/
│   ├── DeviceController.java
│   ├── ThingModelController.java
│   ├── ParseRuleController.java
│   ├── AlarmRuleController.java
│   └── OfflineRuleController.java
├── service/
│   ├── DeviceService.java
│   ├── ParseRuleService.java
│   ├── AlarmRuleService.java
│   └── OfflineRuleService.java
├── service/impl/
│   ├── DeviceServiceImpl.java
│   ├── ParseRuleServiceImpl.java
│   ├── AlarmRuleServiceImpl.java
│   └── OfflineRuleServiceImpl.java
├── mapper/
│   ├── ThingModelMapper.java
│   ├── ThingPropertyMapper.java
│   ├── ThingDeviceMapper.java
│   ├── ParseRuleMapper.java
│   ├── AlarmRuleMapper.java
│   └── OfflineRuleMapper.java
└── producer/
    └── ConfigEventProducer.java
```

### Common Entity
```
iot-common/src/main/java/com/nextiot/common/
├── entity/
│   ├── ThingModel.java
│   ├── ThingProperty.java
│   ├── ThingDevice.java
│   ├── ParseRule.java
│   ├── AlarmRule.java
│   ├── OfflineRule.java
│   ├── AlarmEvent.java
│   ├── AlarmInstance.java
│   ├── MetricData.java
│   └── ConfigChangeEvent.java
├── enums/
│   ├── DataType.java
│   ├── AlarmLevel.java
│   ├── TriggerType.java
│   ├── ConfigChangeType.java
│   └── ConfigOpType.java
├── dto/
│   └── Result.java
└── util/
    └── AviatorUtil.java
```

## 使用示例

### 1. 创建告警规则

```bash
curl -X POST http://localhost:8085/api/alarm-rule/create \
  -H "Content-Type: application/json" \
  -d '{
    "ruleCode": "TEMP_HIGH_5",
    "deviceCode": "dev_001",
    "propertyCode": "temperature",
    "conditionExpr": "value > 90",
    "triggerType": "CONTINUOUS_N",
    "triggerN": 5,
    "suppressSeconds": 120,
    "level": 3,
    "description": "温度连续5次超过90度",
    "enabled": true
  }'
```

### 2. 查询所有告警规则

```bash
curl http://localhost:8085/api/alarm-rule/list
```

### 3. 删除告警规则

```bash
curl -X DELETE http://localhost:8085/api/alarm-rule/TEMP_HIGH_5
```

### 4. 创建离线规则

```bash
curl -X POST http://localhost:8085/api/offline-rule/create \
  -H "Content-Type: application/json" \
  -d '{
    "deviceCode": "dev_001",
    "timeoutSeconds": 600,
    "enabled": true
  }'
```