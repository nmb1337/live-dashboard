# Live Dashboard

实时设备活动仪表盘（前端展示 + 后端数据与面板管理）。

当前仓库已支持：
- 页面直接管理多人面板（新增/删除）
- 后端 `ADMIN_TOKEN` 鉴权
- 前后端分离启动（开发/调试）
- Docker 一体化部署

## 你关心的功能（先看这里）

### 1) 多人面板管理界面在哪？

首页顶部有「多人面板管理」区块（在 `Panels` 切换区上方）。

用途：
- 前端：展示和操作入口
- 后端：`/api/config/dashboards` 持久化管理（SQLite）

### 2) 为什么看不到或不能新增？

常见原因：
1. 前端不是最新构建（容器没 `--build`）
2. `.env` 没有 `ADMIN_TOKEN`
3. 前端分离部署时 `NEXT_PUBLIC_API_BASE` 没指向后端

---

## 目录结构（核心）

- `packages/frontend`：Next.js 前端（展示 + 管理 UI）
- `packages/backend`：Bun + SQLite 后端（数据 + 管理 API）
- `docker-compose.yml`：本仓库默认部署方式
- `.env.example`：环境变量模板（包含 `ADMIN_TOKEN`）
- `deploy/windows-dockerdesktop-local.ps1`：Windows 一键初始化与启动

---

## 环境变量（最小必填）

复制模板：

```powershell
Copy-Item .env.example .env -Force
```

最少确认以下变量：

```env
DEVICE_TOKEN_1=token1:my-pc:My PC:windows
HASH_SECRET=替换为随机字符串
ADMIN_TOKEN=替换为随机字符串
```

建议随机生成：

```powershell
# 32 字节（64 hex）
$HASH_SECRET = -join((1..32) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) })
# 24 字节（48 hex）
$ADMIN_TOKEN = -join((1..24) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) })
```

> `ADMIN_TOKEN` 用于多人面板管理接口鉴权；没有它时，新增/删除会返回 503。

---

## 启动方式 A：Docker 一体化（推荐）

### Windows 一键脚本

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\deploy\windows-dockerdesktop-local.ps1
```

### 手动 Compose

```powershell
Set-Location D:\live-dashboard-main

docker network create --driver bridge --subnet 172.20.0.0/24 your_external_network

docker compose config -q
docker compose up -d --build
```

验证：

```powershell
Invoke-RestMethod http://127.0.0.1:3000/api/health
Invoke-RestMethod http://127.0.0.1:3000/api/config
```

---

## 启动方式 B：前后端分离（重点）

适用场景：你要单独调试前端页面或后端管理接口。

### B1. 启动后端（管理 API 所在）

> 需要 Bun。若主机未安装 Bun，可用 Docker 方式启动整站进行联调。

```powershell
Set-Location D:\live-dashboard-main\packages\backend
bun install

# 当前 PowerShell 会话设置环境变量
$env:PORT = "3000"
$env:DB_PATH = "D:/live-dashboard-main/live-dashboard.db"
$env:HASH_SECRET = "替换为你的 HASH_SECRET"
$env:DEVICE_TOKEN_1 = "替换为你的 DEVICE_TOKEN_1"
$env:ADMIN_TOKEN = "替换为你的 ADMIN_TOKEN"

bun run src/index.ts
```

后端地址：`http://127.0.0.1:3000`

### B2. 启动前端（展示层）

新开一个终端：

```powershell
Set-Location D:\live-dashboard-main\packages\frontend
bun install

# 指向后端
$env:NEXT_PUBLIC_API_BASE = "http://127.0.0.1:3000"

# 避免与后端 3000 端口冲突
bun run dev -- --port 3001
```

前端地址：`http://127.0.0.1:3001`

---

## 多人面板管理使用教程

打开页面后，在「多人面板管理」里填写：
- 管理 Token：`ADMIN_TOKEN`
- 面板 ID：例如 `friend-1`
- 显示名称：例如 `Alice`
- 面板 URL：例如 `https://alice.example.com`
- 描述：可选

点击「添加 / 更新面板」。

删除面板：
- 在管理区底部点击「删除 xxx」。

### 接口说明（后端）

- `POST /api/config/dashboards`
- `DELETE /api/config/dashboards`
- Header: `Authorization: Bearer <ADMIN_TOKEN>`

---

## 常用运维命令

```powershell
# 查看日志
docker logs --tail 100 live_dashboard

# 重启
docker restart live_dashboard

# 重新构建并启动
docker compose up -d --build

# 停止
docker compose down
```

---

## 常见问题

### Q1: 页面没有「多人面板管理」区块

1. 执行 `docker compose up -d --build`
2. 强制刷新浏览器（Ctrl + F5）
3. 确认访问的是当前容器端口（默认 3000）

### Q2: 新增面板失败

检查返回信息：
- `ADMIN_TOKEN not configured on server`：后端没配置 `ADMIN_TOKEN`
- `Unauthorized`：Token 不匹配
- `Invalid dashboard payload`：ID/名称/URL 不合法

### Q3: Docker 构建报 `invalid file request ... node_modules/.bin/tsc`

仓库需要有 `.dockerignore` 排除本地依赖目录。当前仓库已包含该文件。

---

## 文档索引

- `README.md`：部署与多人面板管理主文档
- `docs/android-agent.md`：Android Agent 说明
- `deploy/windows-dockerdesktop-local.ps1`：Windows 本地启动脚本
- `docker-compose.yml`：默认 Compose 配置
- `.env.example`：环境变量模板

## License

MIT
