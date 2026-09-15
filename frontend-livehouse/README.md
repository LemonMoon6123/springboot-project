# LiveHouse 前端工程

这是为 `com.livehouse` 后端接口生成的 Vue 3 + JavaScript 前端工程，包含演出浏览、搜索、抢票、订单支付、电子票核销、图片上传和基础管理工具。

## 本地开发

```bash
cd frontend-livehouse
npm install
npm run dev
```

开发环境访问：

```text
http://localhost:5173
```

Vite 已配置 `/api` 代理到后端：

```text
http://127.0.0.1:8081
```

## 后端要求

启动后端：

```bash
mvn spring-boot:run
```

登录流程：

1. 前端点击“获取验证码”。
2. 查看后端控制台日志中的验证码。
3. 输入验证码登录。

后端鉴权请求头为：

```text
authorization: token
```

前端已按这个规则处理，不使用 `Bearer` 前缀。

## nginx 部署

构建前端：

```bash
cd frontend-livehouse
npm run build
```

将 `frontend-livehouse/nginx/livehouse.conf` 放到 nginx 配置目录，或复制其中的 `server` 配置到你的 `nginx.conf`。

默认路径：

```text
前端 dist：D:/项目/hm-dianping/frontend-livehouse/dist
上传目录：D:/项目/hm-dianping/frontend-livehouse/uploads
后端服务：http://127.0.0.1:8081
```

如果你的项目目录不同，需要同步修改两处：

1. `frontend-livehouse/nginx/livehouse.conf` 中的 `root` 和 `alias`。
2. 后端 `application.yaml` 中的 `livehouse.upload.dir`。

## 图片上传

前端“管理”页面会调用：

```text
POST /api/upload/image
```

表单字段名：

```text
file
```

后端返回：

```text
/uploads/图片文件名
```

nginx 会从 `frontend-livehouse/uploads` 目录回显这些图片。

## 页面模块

- 演出首页：`/show/hot`、`/show/of/name`、`/show/of/city`
- 演出详情：`/show/{id}`、`/venue/{id}`、`/ticket-type/of/show/{showId}`
- 秒杀抢票：`/seckill/ticket/{ticketTypeId}?quantity=1`
- 我的订单：`/order/list`、`/order/pay/{orderId}`
- 电子票核销：`/ticket/verify/{ticketCode}`、`/ticket/detail/{ticketCode}`
- 管理工具：库存、核销统计、限流状态、图片上传
