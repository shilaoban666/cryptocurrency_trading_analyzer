<div align="center">

# 📊 Cryptocurrency Trading Analyzer

**专为 OKX 合约交易者打造的全栈量化分析平台**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![ECharts](https://img.shields.io/badge/ECharts-5.5-AA344D?style=for-the-badge&logo=apache-echarts&logoColor=white)](https://echarts.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

[功能特性](#-功能特性) · [快速开始](#-快速开始) · [系统架构](#-系统架构) · [配置说明](#-配置说明) · [技术栈](#-技术栈)

</div>

---

## ✨ 功能特性

### 📊 总览仪表盘
- **40+ 量化指标**：胜率、盈亏比、夏普比率、卡玛比率、最大回撤、Kelly 仓位等
- **权益曲线**：可视化累计盈亏走势，支持日期范围筛选
- **每日盈亏柱图**：一眼看清盈利日与亏损日分布
- **多空胜率饼图**：做多 vs 做空方向胜率对比
- **杠杆胜率分布**：各杠杆倍数的历史胜率统计

### 🔬 深度量化分析
- **月度绩效表**：每月交易次数、胜率、盈亏、最大回撤综合展示
- **滚动胜率**：20 笔订单窗口的动态胜率折线图
- **时间热力图**：24h × 7d 的交易分布与盈亏热力图，发现最优交易时段
- **品种对比**：各合约品种的交易次数、胜率、总盈亏、平均持仓时长
- **持仓时长分析**：按时长区间统计胜率，找到适合自己的持仓节奏
- **盈亏区间分布**：每笔交易盈亏的频率直方图

### 📋 交易记录
- 全量历史订单分页展示，字段完整（开仓时间、品种、方向、杠杆、均价、盈亏、手续费、持仓时长）
- 多维筛选：品种 / 方向（做多/做空）/ 状态（已成交/已撤销）

### 💥 爆仓分析大屏
- 实时同步强平记录，独立追踪爆仓事件
- **K 线图嵌入爆仓标记点**，直观还原爆仓场景（支持 15m / 1H / 4H / 日线）
- 品种爆仓分布饼图、爆仓高危时段柱图、每日爆仓趋势双轴图
- 关键概览：总爆仓次数、累计损失、最惨单次亏损

### 🌊 主力大户情绪
- **精英多空比**（持仓量）：大户多空方向分歧实时走势
- **多空账户比**：市场情绪指标
- **主动买卖量**（Taker 成交）：资金流方向判断
- **资金费率历史**：费率走势与市场情绪关联
- **持仓量历史**：OI 变化趋势

### 🔍 交易复盘（核心功能）

> 帮助你系统化总结每笔交易的成败原因，建立属于自己的交易知识库

**Tab 1 — 复盘记录**

- 对每笔关仓单记录盈利/亏损原因（9 类预设标签，可多选）
- 亏损单额外记录入场时的技术指标状态：EMA（8 项）/ KDJ（8 项）/ MACD（10 项）
- 自由文本复盘笔记

**Tab 2 — 数据统计**

- 复盘进度环 + 概览卡片（总关仓单 / 已复盘 / 待复盘 / 完成率）
- 盈利原因 Donut 饼图 & 亏损原因 Donut 饼图
- EMA / KDJ / MACD 信号频率水平柱状图
- 盈利原因明细柱图 & 亏损原因明细柱图

### 🎨 UI / UX
- 深色 / 浅色主题一键切换，全页面响应，ECharts 图表随主题自动重绘
- 防闪烁主题加载（`localStorage` 持久化）
- Element Plus 响应式布局，侧边栏导航

---

## 🏗 系统架构

```
┌────────────────────────────────────────────────────────────────┐
│                           Browser                              │
│     Vue 3  ·  Vue Router  ·  Element Plus  ·  ECharts 5       │
│                  Vite Dev Server  :5173                        │
└──────────────────────────┬─────────────────────────────────────┘
                           │  REST API  (Axios + Vite Proxy)
┌──────────────────────────▼─────────────────────────────────────┐
│                  Spring Boot 3.2  :8080                        │
│                                                                │
│  AnalysisController · OrderController · LiquidationController  │
│  MarketController · ReviewController · SyncController          │
│                                                                │
│  AnalysisService · OrderSyncService · OkxApiService            │
│                  LiquidationService                            │
│                                                                │
│                  Spring Data JPA  (Hibernate)                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │   PostgreSQL  —  Liquibase 自动迁移                       │  │
│  │   okx_order · liquidation_record · order_review          │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────┬─────────────────────────────────────┘
                           │  HMAC-SHA256 Signed Requests
               ┌───────────▼────────────┐
               │     OKX REST API v5     │
               │     www.okx.com        │
               └────────────────────────┘
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 最低版本 |
|------|----------|
| JDK | 17 |
| Maven | 3.8 |
| Node.js | 18 |
| PostgreSQL | 14 |

### 1. 克隆仓库

```bash
git clone git@github.com:shilaoban666/cryptocurrency_trading_analyzer.git
cd cryptocurrency_trading_analyzer
```

### 2. 创建数据库

```sql
CREATE DATABASE okx_analyzer;
```

### 3. 配置后端

```bash
cp backend/src/main/resources/application.yml.example \
   backend/src/main/resources/application.yml
```

编辑 `application.yml`，填写数据库连接和 OKX API 密钥：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/okx_analyzer
    username: postgres
    password: your_db_password

okx:
  api:
    key: YOUR_OKX_API_KEY        # OKX -> 个人中心 -> API 管理（只读权限）
    secret: YOUR_OKX_SECRET
    passphrase: YOUR_PASSPHRASE
    simulated: false             # true = 模拟盘
    proxy-host: 127.0.0.1       # 国内需要，留空则不走代理
    proxy-port: 7890
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

> Liquibase 会在首次启动时自动建表，无需手动执行任何 SQL。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开浏览器访问：**http://localhost:5173**

### 6. 同步数据

点击侧边栏顶部的 **「同步数据」** 按钮，系统将从 OKX 拉取历史合约订单，首次同步约需 10~30 秒。

---

## ⚙️ 配置说明

### OKX API 密钥

登录 OKX → 个人中心 → API 管理 → 创建 API Key，权限仅需勾选**读取**，无需交易或提现权限。

### 国内代理

在中国大陆访问 OKX API，需配置本地代理（Clash / V2Ray 等）：

```yaml
okx:
  api:
    proxy-host: 127.0.0.1
    proxy-port: 7890   # 改为你本地代理的实际端口
```

不需要代理时，注释掉或删除这两行即可。

### 历史数据范围

```yaml
okx:
  api:
    history-months: 6   # 拉取最近 N 个月（OKX 归档接口实际上限约 3 个月）
```

---

## 🗄 数据库结构

```
okx_order                   合约订单（SWAP / FUTURES）
  ordId · instId · instType · posSide · sz · avgPx
  pnl · fee · lever · holdingMinutes · isWin · isLiquidation
  state · createTime · updateTime · syncedAt

liquidation_record          强平（爆仓）记录
  billId · instId · posSide · pnl · fee · sz · px
  balanceAfter · liquidationTime · syncedAt

order_review                复盘标注
  ordId · reviewText · reasons · emaSignals
  kdjSignals · macdSignals · createdAt · updatedAt
```

---

## 📂 项目结构

```
cryptocurrency_trading_analyzer/
├── backend/
│   └── src/main/
│       ├── java/com/okx/analyzer/
│       │   ├── controller/          REST 控制器
│       │   ├── service/             业务逻辑（OKX API 对接、指标计算）
│       │   ├── entity/              JPA 实体
│       │   ├── repository/          数据访问层
│       │   ├── dto/                 数据传输对象（40+ 分析字段）
│       │   └── config/              CORS、OKX 配置类
│       └── resources/
│           ├── application.yml.example   配置模板（复制后填入密钥）
│           └── db/changelog/             Liquibase 迁移脚本（001~004）
│
└── frontend/
    └── src/
        ├── views/
        │   ├── Dashboard.vue        总览仪表盘
        │   ├── Analysis.vue         深度量化分析（16 张图）
        │   ├── Orders.vue           交易记录列表
        │   ├── Liquidation.vue      爆仓分析大屏
        │   ├── MarketSentiment.vue  主力大户情绪
        │   └── Review.vue           交易复盘（含统计标签页）
        ├── composables/
        │   └── useTheme.js          深色/浅色主题 Composable
        ├── api/index.js             全量 API 调用封装
        ├── router/index.js          路由配置
        └── App.vue                  根组件（布局 + 侧边栏 + 主题切换）
```

---

## 🛠 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.3 | Web 框架 |
| Spring Data JPA | — | ORM 层 |
| PostgreSQL | 16 | 关系型数据库 |
| Liquibase | — | 数据库版本管理 |
| Lombok | — | 减少样板代码 |
| Jackson | — | JSON 序列化 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4.21 | UI 框架 |
| Vue Router | 4.3.0 | 前端路由 |
| Element Plus | 2.6.1 | UI 组件库 |
| ECharts | 5.5.0 | 数据可视化 |
| Axios | 1.6.8 | HTTP 客户端 |
| Day.js | 1.11.10 | 日期处理 |
| Vite | 5.2.0 | 构建工具 |

---

## 🔒 安全说明

- `application.yml` 已加入 `.gitignore`，API 密钥不会提交到版本库
- OKX API Key 仅需**读取**权限，不涉及交易或提现操作
- 所有数据存储在本地 PostgreSQL，不上传任何第三方服务

---

## 📄 License

[MIT License](LICENSE) © 2024

---

<div align="center">

如果这个项目对你有帮助，欢迎点个 ⭐ **Star**！

</div>
