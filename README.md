# 智购商城 · AI 智能电商平台 · Agent 版

基于 Spring Boot 3 + Vue 3 实现的多角色电商系统，功能对齐「京东超市购物商城-商家版」，并增加 **Spring AI Agent（导购 / 加购 / 下单 / 支付）**、**RAG 知识库**、**AI 客服** 与 **商家注册审核**。

## 技术栈

- 后端：Spring Boot 3.3、MyBatis-Plus、MySQL、JWT、Spring AI 1.0
- 前端：Vue 3、Vite、Element Plus、Pinia、ECharts

## 角色说明

| 角色 | 入口 | 说明 |
|------|------|------|
| 用户 | `/login` → `/user/home` | 浏览、购物车、下单、收藏、AI 导购 Agent / 客服 |
| 商家 | `/login` → `/merchant/home` | 需管理员审核通过后才能登录 |
| 管理员 | `/admin/login` → `/admin/home` | 分类/商品/订单/商家审核/公告/统计等 |

## 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `user` | `123456` | 普通用户（含余额） |
| `merchant` | `123456` | 已审核商家 |
| `merchant2` | `123456` | 待审核商家（无法登录） |
| `admin` | `123456` | 管理员 |

## 启动

### 1. 数据库

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 admin_scaffold < backend/src/main/resources/schema-ecommerce.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 admin_scaffold < backend/src/main/resources/data-ecommerce.sql
# 若中文乱码，再执行：
mysql -uroot -p123456 --default-character-set=utf8mb4 admin_scaffold < backend/src/main/resources/fix-chinese-data.sql
```

数据库名默认 `ai_mall`，账号密码见 `backend/src/main/resources/application.yml`。

首次初始化（推荐用仓库根目录最新导出）：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 < ../sql/ai_mall.sql
```

或：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 < backend/src/main/resources/init-ai-mall.sql
```

### 2. 后端

```bash
cd backend
mvn spring-boot:run
```

服务：`http://localhost:8080/api`

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

访问：`http://127.0.0.1:5173`

## 核心功能

- 商城首页：轮播、分类、热销、推荐、公告
- 商品：分类筛选、搜索、详情、评价、问答、收藏
- 交易：购物车、收货地址、余额支付、发货、确认收货
- 商家：商品上下架、订单发货、评价/问答管理、经营统计
- 管理：商家审核、全站商品/订单、公告、轮播、ECharts 统计
- AI 导购 Agent：文字/识图推荐；登录后可 Tool Calling 加购、结算、模拟支付
- RAG：`resources/rag/*.md` 政策文档，售后/物流优先检索
- AI 客服：订单/物流/退换货问答（可接大模型）
- 管理端：`/admin/agent` 工具总览、`/admin/rag` 知识库

## AI 配置（DeepSeek）

在 `application.yml` 或 `sys_config` 表配置：

- `ai_api_key`：DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com/)）
- `ai_api_url`：`https://api.deepseek.com/chat/completions`
- `ai_model`：`deepseek-chat`（AI 导购 / AI 客服文字）
- `ai_vision_model`：`deepseek-v4-flash-vision-exp`（AI 识图）
- `ai_enabled`：`true`

未配置密钥时使用本地规则引擎；配置后用户端 `/user/ai-guide` 走 `POST /api/ai/agent`。
