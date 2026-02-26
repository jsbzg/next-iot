# CLAUDE.md

## 一、项目定位与目标

本项目是一个 **IoT 物联网平台**，采用 **Spring Cloud + Vue** 技术栈，目标是构建：

- 可水平扩展的后端微服务体系
- 支持高并发设备接入与数据处理
- 前后端分离、工程化、可持续演进
- 适合中大型团队协作与长期维护

Claude 在本项目中充当 **高级工程协作助手**，辅助但不替代工程师决策。

---

## 二、语言与输出规范（强制）

1. 所有回答 **必须使用简体中文**
2. 不使用中英混排（代码、类名、库名除外）
3. 输出内容优先工程可落地，而非概念性描述
4. 不输出与当前问题无关的内容

---

## 三、项目约束

### 3.1、目标与边界

#### 3.1.1 目标

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

#### 3.1.2 非目标（Demo 简化）

- 不做 Flink / Kafka / MySQL 高可用
- 不做多机房、多集群
- Redis / 时序库暂用 MySQL 替代（文中会标注替换点）

------

### 3.2、总体架构

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

### 3.3、核心设计原则

1. **架构核心理念：String-First**

   - 所有原始报文保持 String 格式
   - 格式判断、字段提取、类型转换、数组展开等逻辑 **完全由 AviatorScript 动态配置完成**
   - Flink 负责执行脚本，不预先解析结构

2. **Flink = 智能水龙头**

   - 必须：
     - 报文解析（基于 AviatorScript，支持任意格式）
     - 设备合法性校验
     - 做告警检测（连续 N 次、窗口判断）
     - 做流内抑制（防刷屏、时间窗口去重）
     - 离线检测
   - 目标：**不让脏数据、不合规设备、不受控告警洪水进入下游**

3. **Alarm Service = 用水管家**

   - 只做：
     - 业务策略抑制（维护窗口、静默策略）
     - 生命周期管理（ACTIVE / ACK / RECOVERED）
     - 通知编排（短信 / 邮件 / Webhook）

4. **所有规则 + 设备主数据必须支持动态刷新**

   - 改配置：
     - 不允许重启 Flink
   - 手段：
     - MySQL 持久化
     - Kafka config-topic 广播
     - Flink Broadcast State 实时更新

5. **设备合法性过滤是数据入口的第一道业务闸门**

   - 位置：Flink 中，「解析完成之后、检测之前」
   - 目标：防止污染 Flink State、指标流、告警流

------

### 3.4、数据模型设计（MySQL）

> ⚠️ Demo 阶段用 MySQL
> ⚠️ 后续可替换：
>
> - 状态类表 → Redis
> - 时序数据 → ClickHouse / TDengine / InfluxDB

#### 3.4.1 物模型（thing_model）

```sql
thing_model(
  id,
  model_code,
  model_name
)
```

#### 3.4.2 点位定义（thing_property）

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

#### 3.4.3 设备实例（thing_device，作为“设备白名单主数据”）

```sql
thing_device(
  id,
  device_code,
  model_code,
  gateway_type
)
```

#### 3.4.4 解析规则（parse_rule）

```sql
parse_rule(
  id,
  gateway_type,
  protocol_type,       -- mqtt/http/tcp
  match_expr,          -- Aviator: 判断是否命中该规则
  parse_script,        -- Aviator: 从原始报文提取字段
  mapping_script,      -- Aviator: 映射为标准格式（可选）
  parse_mode,          -- 解析模式：STRING（新） / LEGACY（旧）
  key_extractor_script, -- Key 提取脚本（可选，用于优化 keyBy）
  parser_type,         -- 解析器类型：JSON/CSV/PIPE/FIXED_WIDTH/HEX/CUSTOM
  sample_input,        -- 示例输入（用于规则调试）
  validation_regex,    -- 验证正则表达式（可选）
  version,
  enabled
)
```

**支持的报文格式（通过 AviatorScript 配置）**：

| 格式类型 | 示例输入 | parser_type | 典型脚本 |
|---------|---------|-------------|---------|
| JSON 对象 | `{"deviceCode":"dev001","temperature":26.5}` | JSON | `m.deviceCode = rawMap.deviceCode; m.value = rawMap.temperature; return m;` |
| JSON 数组 | `[{"deviceCode":"dev001","temp":26.5},...]` | JSON | `for item in rawMap { m.deviceCode = item.deviceCode; ... }` |
| CSV | `dev_001,1771913823,26.7,44.8,1011.2` | CSV | `let fields = split(rawMessage, ','); m.value = double(fields[2]);` |
| 管道符 | `dev_001\|1771913823\|26.7` | PIPE | `let parts = split(rawMessage, '\|'); m.value = double(parts[2]);` |
| 固定长度 | `dev0012025010126.700448.8` | FIXED_WIDTH | `m.deviceCode = substring(rawMessage, 0, 7); m.temp = substring(rawMessage, 15, 22);` |
| Modbus Hex | `$DEV001,26.5*4A` | HEX | `let checksum = hex_to_int(substring(rawMessage, starIndex+1, -1));` |

**AviatorScript 自定义函数**：

| 函数 | 说明 | 示例 |
|-----|------|-----|
| `split(str, delimiter)` | 字符串分割 | `split("a,b,c", ",")` → `["a", "b", "c"]` |
| `substring(str, start, end)` | 子串提取 | `substring("hello", 1, 4)` → `"ell"` |
| `length(str)` | 字符串长度 | `length("hello")` → `5` |
| `index_of(str, sub)` | 查找子串索引 | `index_of("hello", "ll")` → `2` |
| `hex_to_int(hex)` | 十六进制转整数 | `hex_to_int("4A")` → `74` |
| `byte_at(str, idx)` | 获取字节值 | `byte_at("AB", 0)` → `65` |
| `replace(str, target, repl)` | 字符串替换 | `replace("hello", "l", "L")` → `"heLLo"` |

#### 3.4.5 告警规则（alarm_rule）

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

#### 3.4.6 离线规则（offline_rule）

```sql
offline_rule(
  id,
  device_code,
  timeout_seconds      -- 超过多久算离线
)
```

> ⚠️ 离线状态当前可存在 MySQL，后续应迁移 Redis / 状态库

------

### 3.5、Kafka 设计

| Topic             | 作用                                           |
| ----------------- | ---------------------------------------------- |
| raw-topic         | 原始网关报文                                   |
| metric-topic      | Flink 输出的标准化点位数据                     |
| alarm-event-topic | Flink 输出的“已检测 + 已流内抑制”的告警事件    |
| config-topic      | 配置变更事件（解析规则 / 告警规则 / 离线规则） |
| dirty-topic       | 非法设备 / 解析失败数据 SideOutput             |

 配置变更事件（统一模型）:

```json
{
  "type": "DEVICE | PARSE_RULE | ALARM_RULE | OFFLINE_RULE",
  "op": "ADD | UPDATE | DELETE",
  "payload": { ... }
}
```

------

### 3.6、Flink Job 设计（核心）

#### 3.6.1 输入流

- 主流：`raw-topic`（原始 String 消息，包装为 `IntermediateRawMessage`）
- 广播流：`config-topic`

#### 3.6.2 Broadcast State

- deviceState: `MapState<String, ThingDevice>` - 设备白名单
- parseRuleState: `MapState<String, ParseRule>` - 解析规则
- alarmRuleState: `MapState<String, List<AlarmRule>>` - 告警规则
- offlineRuleState: `MapState<String, OfflineRule>` - 离线规则

结构示例：ParseRule 和 OfflineRule 的 key 为 `gatewayType` 或 `deviceCode`

------

#### 3.6.3 处理流程

##### Step 0：数据源包装

- 原始消息保持 String 格式，包装为 `IntermediateRawMessage`
- `rawMessage`: 原始报文字符串
- `gatewayType`: 根据 `extractGatewayType()` 方法判断（JSON/CSV/PIPE/FIXED_WIDTH 等）

**Fallback Key 策略**：
- 使用 `gatewayType + rawMessage.hashCode()` 作为 key
- 优点：改动最小，不重构整个流架构，向后兼容性好

-----

##### Step 1：动态解析（基于 AviatorScript）

**核心理念**：
- 保持原始报文为 String
- 所有格式判断、字段提取、类型转换由 AviatorScript 完成

**处理流程**：
1. 构建 Aviator 环境：
   - `rawMessage`: 原始字符串
   - `rawMap`: JSON 解析后的 Map（向后兼容）
   - `now`: 当前时间戳
2. 执行 `matchExpr`：判断是否匹配该解析规则
3. 执行 `parseScript`：提取字段，返回 Map 或 List<Map>
4. 执行 `mappingScript`（可选）：映射为标准格式
5. 规范化为 `List<MetricData>`

**AviatorScript 环境变量**：
```javascript
// 新模式：String-First
rawMessage      // 原始报文字符串
now             // 当前时间戳

// 向后兼容：如果原始报文是 JSON，也会提供
rawMap          // JSON 解析后的 Map
raw             // 同 rawMap
rawMap.field    // 扁平化字段（如 rawMap.deviceCode）
```

**AviatorScript 返回格式**：
- 单个 Map：`{"deviceCode": "dev001", "propertyCode": "temperature", "value": 26.5, "ts": 1690000000}`
- List<Map>：`[{"deviceCode": "dev001", ...}, {"deviceCode": "dev002", ...}]` ---

##### Step 2：设备合法性校验

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

##### Step 3：检测规则（必须在 Flink）

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

##### Step 4：流内抑制（必须在 Flink）

在触发告警前，执行：

- 同一规则 + 同一设备
- 在 X 秒内只允许输出 1 次
- 使用：
  - ValueState

目的：

> 防止 1 秒 100 条相同告警打爆下游

##### Step 5：离线检测（必须在 Flink）

- KeyBy(deviceCode)
- 维护：
  - ValueState
- 定时器（ProcessingTime 或 EventTime）：
  - 如果 now - lastSeenTs > offlineRule.timeoutSeconds
  - 输出 OFFLINE 告警事件（走同样 alarm-event-topic）
- offlineRule 来自 Broadcast State（支持动态刷新）

------

##### Step 6：输出告警事件

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

### 3.7、规则动态刷新机制

#### 3.7.1 配置后台

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

#### 3.7.2 Flink 侧

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

### 3.8、非法设备数据处理策略

#### 3.8.1 判定标准

- 解析完成后：

  - 如果 deviceCode 不存在于 Broadcast 的 thing_device 状态中

  - 即判定为：

    > 非法 / 未注册 / 已删除设备数据

#### 3.8.2 处理方式

- 推荐：
  - SideOutput 到 `dirty-topic`
  - 同时打日志
- 严禁：
  - 进入检测状态
  - 进入抑制状态
  - 进入告警链路

#### 3.8.3 工程目的

- 防止：
  - Flink State 被污染
  - 连续 N 次计数错误
  - 离线状态误判
  - 下游告警风暴

------

### 3.9、Alarm Service 设计

#### 3.9.1 输入

- 消费 `alarm-event-topic`

#### 3.9.2 处理逻辑

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

#### 3.9.3 存储（MySQL）

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

### 3.10、前端功能

1. 物模型管理
2. 设备管理
3. 解析规则管理（Aviator 脚本编辑）
4. 告警规则管理（连续 N 次 / 窗口参数）
5. 离线规则管理
6. 告警列表查看
6. 大屏数据展示（简单即可，无需复杂特效）

------

### 3.11、技术栈约束

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

### 3.12、关键可替换点（必须在代码中标注）

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

### 3.13、模块划分

```
iot-data-platform/
├── pom.xml                      # 父 POM（统一版本管理）
├── iot-common/                  # 公共模型 & 工具
├── iot-config-service/          # 配置中心 / 后台管理服务
├── iot-alarm-service/           # 告警服务
├── iot-flink-job/               # Flink 实时解析 + 检测 + 抑制 Job
└── iot-frontend/                # 前端管理页面（可选，或独立仓库）
```

---

## 四、Claude 的工作边界

Claude 可以：
- 生成代码模板
- 设计模块结构
- 优化现有实现
- 分析性能与扩展性问题

Claude 不可以：
- 编造不存在的业务逻辑
- 隐含修改既定架构决策
- 无理由引入新技术栈

---

## 五、自动维护规则（重要）

当用户提出以下请求时，Claude **可以建议更新本文件**：

- 架构发生重大调整
- 技术栈升级（如 Spring Boot / Vue 大版本）
- 新增核心业务域（如规则引擎、告警中心）

除非用户明确要求，Claude **不得自动修改本文件内容**。

---

