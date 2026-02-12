# 一、目标与边界

## 1.1 目标

构建一个 IoT 数据中台 Demo，满足：

1. 支持多网关 / 多协议 / 多报文格式接入
2. 新增网关类型或设备：
   - 只需在页面配置解析规则 / 设备信息 / 告警规则
   - **无需重启 Flink Job**
3. Flink 侧必须完成：
   - 动态解析
   - 动态规则检测（连续 N 次 / 窗口）
   - **流内抑制（防刷屏 / 窗口去重）**
   - **非法设备数据过滤**
   - **离线检测**
4. Alarm Service 侧只负责：
   - 业务抑制（维护窗口 / 静默策略 / 人工 ACK 后抑制）
   - 生命周期管理
   - 通知编排
5. 所有规则 / 设备主数据：
   - 存 MySQL
   - 通过 Kafka `config-topic` **动态下发**
   - Flink 使用 **Broadcast State 实时生效**

## 1.2 非目标（Demo 简化）

- 不做 Flink / Kafka / MySQL 高可用
- 不做多机房、多集群
- Redis / 时序库暂用 MySQL 替代（文中会标注替换点）

------

# 二、总体架构

```less
[ 网关设备 ]
     |
     v
[ Kafka: raw-topic ]
     |
     v
[ Flink Job ]
  1. 动态解析（ParseRule, Aviator）
  2. 设备合法性校验（ThingDevice 广播状态）
     - 不在库 → side-output / 丢弃
  3. 动态检测规则（AlarmRule, Aviator）
     - 连续N次 / 窗口
  4. 流内抑制（State 防刷屏）
  5. 离线检测（OfflineRule + 定时器）
     |
     +--> [ Kafka: metric-topic ]（可选）
     |
     +--> [ Kafka: alarm-event-topic ]（已检测 + 已抑制）
                                     |
                                     v
                              [ Alarm Service ]
                               - 业务抑制
                               - 生命周期
                               - 通知编排
                                     |
                                     v
                                [ MySQL: alarm_* ]

[ 配置服务 Config Service ]
   - 管理：物模型 / 设备 / 解析规则 / 告警规则 / 离线规则
   - 写 MySQL
   - 发送 ConfigChangeEvent -> Kafka: config-topic
                           |
                           v
                     [ Flink Broadcast Stream ]
                     动态刷新所有规则 + 设备主数据

```

------

# 三、核心设计原则

1. **Flink = 智能水龙头**
   - 必须：
     - 报文解析
     - 设备合法性校验
     - 做告警检测（连续 N 次、窗口判断）
     - 做流内抑制（防刷屏、时间窗口去重）
     - 离线检测
   - 目标：**不让脏数据、不合规设备、不受控告警洪水进入下游**
   
2. **Alarm Service = 用水管家**
   
   - 只做：
     - 业务策略抑制（维护窗口、静默策略）
     - 生命周期管理（ACTIVE / ACK / RECOVERED）
     - 通知编排（短信 / 邮件 / Webhook）
   
3. **所有规则 + 设备主数据必须支持动态刷新**

   - 改配置：
     - 不允许重启 Flink
   - 手段：
     - MySQL 持久化
     - Kafka config-topic 广播
     - Flink Broadcast State 实时更新

4. **设备合法性过滤是数据入口的第一道业务闸门**

   - 位置：

     > Flink 中，“解析完成之后、检测之前”

   - 目标：

     - 防止污染：
       - Flink State
       - 指标流
       - 告警流

------

# 四、数据模型设计（MySQL）

> ⚠️ Demo 阶段用 MySQL
> ⚠️ 后续可替换：
>
> - 状态类表 → Redis
> - 时序数据 → ClickHouse / TDengine / InfluxDB

## 4.1 物模型（thing_model）

```sql
thing_model(
  id,
  model_code,
  model_name
)
```

## 4.2 点位定义（thing_property）

```sql
thing_property(
  id,
  model_code,
  property_code,
  property_name,
  data_type,      -- int/double/string/bool
  unit
)
```

## 4.3 设备实例（thing_device，作为“设备白名单主数据”）

```sql
thing_device(
  id,
  device_code,
  model_code,
  gateway_type
)
```

## 4.4 解析规则（parse_rule）

```sql
parse_rule(
  id,
  gateway_type,
  protocol_type,       -- mqtt/http/tcp
  match_expr,          -- Aviator: 判断是否命中该规则
  parse_script,        -- Aviator: 从原始报文提取字段
  mapping_script,      -- Aviator: 映射为 {deviceCode, propertyCode, value, ts}
  version,
  updated_at
)
```

## 4.5 告警规则（alarm_rule）

```sql
alarm_rule(
  id,
  rule_code,
  device_code,         -- 可为空（表示按模型）
  property_code,
  condition_expr,      -- Aviator: value > 80
  trigger_type,        -- CONTINUOUS_N / WINDOW
  trigger_n,           -- 连续N次
  window_seconds,      -- 窗口大小
  level,
  enabled
)
```

## 4.6 离线规则（offline_rule）

```sql
offline_rule(
  id,
  device_code,
  timeout_seconds      -- 超过多久算离线
)
```

> ⚠️ 离线状态当前可存在 MySQL，后续应迁移 Redis / 状态库

------

# 五、Kafka 设计

| Topic               | 作用                                           |
| ------------------- | ---------------------------------------------- |
| raw-topic           | 原始网关报文                                   |
| metric-topic        | Flink 输出的标准化点位数据                     |
| alarm-event-topic   | Flink 输出的“已检测 + 已流内抑制”的告警事件    |
| config-topic        | 配置变更事件（解析规则 / 告警规则 / 离线规则） |
| dirty-topic（可选） | 非法设备 / 解析失败数据 SideOutput             |

 配置变更事件（统一模型）:

```json
{
  "type": "DEVICE | PARSE_RULE | ALARM_RULE | OFFLINE_RULE",
  "op": "ADD | UPDATE | DELETE",
  "payload": { ... }
}
```

------

# 六、Flink Job 设计（核心）

## 6.1 输入流

- 主流：`raw-topic`
- 广播流：`config-topic`

## 6.2 Broadcast State

- parseRuleState
- alarmRuleState
- offlineRuleState

结构示例：

```java
MapState<String, ParseRule>
MapState<String, AlarmRule>
MapState<String, OfflineRule>
```

Key是gatewayType + version

------

## 6.3 处理流程

### Step 1：动态解析

- 使用：
  - ParseRule（Broadcast State）
  - AviatorScript
- 输出：

```json
{
  "deviceCode": "dev_001",
  "propertyCode": "temperature",
  "value": 85.3,
  "ts": 1690000000
}
```

### Step 2：设备合法性校验

- 使用：
  - BroadcastState<String, ThingDevice>
- 逻辑：
  - 根据 deviceCode 查设备状态表
  - 如果不存在：
    - SideOutput 到 dirty-topic 或日志
    - **直接丢弃，不进入后续流程**
  - 如果存在：
    - 进入检测流程

> 这是 Flink 中的**第一道业务闸门**

------

### Step 3：检测规则（必须在 Flink）

- KeyBy(deviceCode + propertyCode)
- 按 alarm_rule 执行：
  - 连续 N 次判断
  - 窗口内判断
- 内部使用：
  - ValueState / ListState 记录最近 N 次状态
  - 或滑动窗口计数

示例语义：

> dev_001.temperature 连续 3 次 > 80

------

### Step 4：流内抑制（必须在 Flink）

在触发告警前，执行：

- 同一规则 + 同一设备
- 在 X 秒内只允许输出 1 次
- 使用：
  - ValueState

目的：

> 防止 1 秒 100 条相同告警打爆下游

### Step 5：离线检测（必须在 Flink）

- KeyBy(deviceCode)
- 维护：
  - ValueState
- 定时器（ProcessingTime 或 EventTime）：
  - 如果 now - lastSeenTs > offlineRule.timeoutSeconds
  - 输出 OFFLINE 告警事件（走同样 alarm-event-topic）
- offlineRule 来自 Broadcast State（支持动态刷新）

------

### Step 4：输出告警事件

输出到 `alarm-event-topic`：

```json
{
  "ruleCode": "TEMP_HIGH_3",
  "deviceCode": "dev_001",
  "propertyCode": "temperature",
  "value": 85.3,
  "ts": 1690000123
}
```

------

# 七、规则动态刷新机制

## 7.1 配置后台

当发生：
- 新增 / 修改 / 删除：
  - 设备
  - 解析规则
  - 告警规则
  - 离线规则

必须：

1. 更新 MySQL
2. 发送 `ConfigChangeEvent` 到 `config-topic`

------

## 7.2 Flink 侧

- config-topic 作为 Broadcast Stream
- 在 `processBroadcastElement` 中：
  - 按 type：
    - 更新 deviceState
    - 更新 parseRuleState
    - 更新 alarmRuleState
    - 更新 offlineRuleState
- 主流处理时：
  - 实时读取最新Broadcast State
- **无需重启 Flink Job**

# 八、非法设备数据处理策略

## 8.1 判定标准

- 解析完成后：

  - 如果 deviceCode 不存在于 Broadcast 的 thing_device 状态中

  - 即判定为：

    > 非法 / 未注册 / 已删除设备数据

## 8.2 处理方式

- 推荐：
  - SideOutput 到 `dirty-topic`
  - 同时打日志
- 严禁：
  - 进入检测状态
  - 进入抑制状态
  - 进入告警链路

## 8.3 工程目的

- 防止：
  - Flink State 被污染
  - 连续 N 次计数错误
  - 离线状态误判
  - 下游告警风暴

------

# 九、Alarm Service 设计

## 9.1 输入

- 消费 `alarm-event-topic`

## 9.2 处理逻辑

只做：

1. 业务抑制：
   - 维护窗口
   - 静默策略
   - 人工 ACK 后的抑制
2. 生命周期管理：
   - ACTIVE
   - ACKED
   - RECOVERED
3. 通知编排：
   - Webhook / 邮件 / 短信（Demo 可只打日志）

## 9.3 存储（MySQL）

```sql
alarm_instance(
  id,
  rule_code,
  device_code,
  status,
  first_trigger_time,
  last_trigger_time
)
```

> ⚠️ 后续高并发应迁移 Redis / ES

------

# 九、前端功能（Demo）

1. 物模型管理
2. 设备管理
3. 解析规则管理（Aviator 脚本编辑）
4. 告警规则管理（连续 N 次 / 窗口参数）
5. 离线规则管理
6. 告警列表查看
6. 大屏数据展示（简单即可，无需复杂特效）

------

# 十、技术栈约束

- 后端：
  - JDK 17
  - Spring Boot3.+
  - Maven
  - MySQL
  - Kafka Client
- Flink：
  - Flink 1.18+（建议）
  - AviatorScript 作为表达式引擎
- 前端：
  - Vue3 + ElementPlus

------

# 十一、关键可替换点（必须在代码中标注）

1. **规则状态缓存**
   - 当前：MySQL + Flink State
   - 未来：MySQL + Redis + 配置中心
2. **时序数据存储**
   - 当前： MySQL
   - 未来：ClickHouse / TDengine
3. **告警实例状态**
   - 当前：MySQL
   - 未来：Redis + ES

------

# 十二、模块划分

```
iot-data-platform/
├── pom.xml                      # 父 POM（统一版本管理）
├── iot-common/                  # 公共模型 & 工具
├── iot-config-service/          # 配置中心 / 后台管理服务
├── iot-alarm-service/           # 告警服务
├── iot-flink-job/               # Flink 实时解析 + 检测 + 抑制 Job
└── iot-frontend/                # 前端管理页面（可选，或独立仓库）
```

