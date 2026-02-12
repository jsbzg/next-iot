# IoT 前端项目

基于 Vue 3 + Element Plus + Vite + npm 的物联网数据中台前端管理系统

## 技术栈

- Vue 3 (Composition API)
- Vite build tool
- Element Plus UI 框架
- Axios HTTP 客户端
- ECharts 图表库
- Dayjs 时间处理库
- Pinia 状态管理
- Vue Router 路由管理

## 项目结构

```
iot-frontend/
├── src/
│   ├── api/           # API 接口
│   │   ├── device.js    # 设备管理 API
│   │   ├── model.js     # 物模型管理 API
│   │   ├── parseRule.js # 解析规则管理 API
│   │   ├── alarmRule.js # 告警规则管理 API
│   │   ├── offlineRule.js # 离线规则管理 API
│   │   └── alarm.js     # 告警实例 API
│   ├── views/          # 页面组件
│   │   ├── Dashboard.vue       # 数据大屏
│   │   ├── Devices.vue         # 设备管理
│   │   ├── Models.vue          # 物模型管理
│   │   ├── ParseRules.vue      # 解析规则管理（含 Aviator 脚本编辑）
│   │   ├── AlarmRules.vue      # 告警规则管理（连续N次/窗口）
│   │   ├── OfflineRules.vue    # 离线规则管理
│   │   └── Alarms.vue          # 告警列表查看
│   ├── router/         # 路由配置
│   ├── utils/          # 工具函数
│   ├── components/     # 公共组件
│   ├── stores/         # 状态管理
│   ├── App.vue         # 根组件
│   └── main.js         # 入口文件
├── public/            # 静态资源
├── index.html         # HTML 模板
├── package.json        # 项目配置
└── vite.config.js     # Vite 配置
```

## 功能模块

### 1. 数据大屏 (Dashboard)
- 设备总数、在线设备统计
- 活跃告警、严重告警统计
- 设备在线状态饼图
- 告警级别分布饼图
- 告警趋势折线图（近7天）

### 2. 设备管理 (Devices)
- 设备列表展示
- 设备搜索（按设备编码、物模型）
- 新增/编辑/删除设备
- 设备在线状态显示
- 设备最后上报时间显示

### 3. 物模型管理 (Models)
- 物模型列表展示
- 新增/删除物模型
- 查看和管理点位
- 新增/删除点位
- 点位数据类型管理（整数/浮点数/字符串/布尔值）

### 4. 解析规则管理 (ParseRules)
- 解析规则列表展示
- 新增解析规则
- Aviator 脚本编辑器
  - 匹配表达式
  - 解析脚本
  - 映射脚本
- Aviator 表达式语法提示
- 版本管理
- 规则启用/禁用

### 5. 告警规则管理 (AlarmRules)
- 告警规则列表展示
- 新增告警规则
- 配置触发类型
  - 连续 N 次 (CONTINUOUS_N)
  - 时间窗口 (WINDOW)
- 配置触发参数
  - 触发次数 N
  - 窗口大小（秒）
- 配置流内抑制时间（秒）
- 配置告警级别（提示/警告/严重/紧急）
- Aviator 条件表达式编辑
- 设备级/模型级规则配置

### 6. 离线规则管理 (OfflineRules)
- 离线规则列表展示
- 新增离线规则
- 配置设备编码
- 配置超时时长（秒/分钟）
- 规则启用/禁用

### 7. 告警列表 (Alarms)
- 告警列表展示
- 告警搜索（按状态、设备编码）
- 多选批量确认
- 单个告警确认
- 告警详情查看
- 告警状态展示（活跃/已确认/已恢复）
- 分页功能

## 快速开始

### 1. 安装依赖

```bash
cd iot-frontend
npm install
# 或使用 pnpm
pnpm install
```

### 2. 启动开发服务器

```bash
npm run dev
# 访问 http://localhost:3000
```

### 3. 构建生产版本

```bash
npm run build
```

### 4. 预览生产版本

```bash
npm run preview
```

## API 代理配置

开发环境已配置 API 代理：

- `/api/*` -> `http://localhost:8085/api/*` (Config Service)
- `/alarm/*` -> `http://localhost:8086/*` (Alarm Service)

生产环境部署时需要配置反向代理或修改 API 基础路径。

## 主要特性

### 响应式设计
- 支持桌面端和平板设备
- 固定侧边栏布局
- 流式页面内容

### Aviator 表达式编辑器
- 内置 Aviator 语法提示
- 支持常见表达式类型
- 文档链接指向官方文档

### 表单验证
- 使用 Element Plus 的表单验证规则
- 必填字段验证
- 数据范围验证

### 数据交互
- 使用 Axios 进行 HTTP 请求
- 统一的响应拦截处理
- 错误提示

### 状态管理
- 页面级别的响应式状态
- 无需全局状态管理（按需使用 Pinia）

### 图表展示
- 使用 ECharts 绘制图表
- 响应式图表大小
- 图表工具提示

## 常见问题

### npm install 慢
建议使用国内镜像源：
```bash
npm config set registry https://registry.npmmirror.com
```

### 代理不生效
确保后端服务已启动：
- Config Service (端口 8085)
- Alarm Service (端口 8086)

### Vite 端口冲突
修改 `vite.config.js` 中的 `server.port` 配置。

## 浏览器支持

- Chrome >= 87
- Firefox >= 78
- Safari >= 14
- Edge >= 88