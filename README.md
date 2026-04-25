# Live Dashboard

这是一个可以实时展示“你正在用什么应用”的网页看板。

你要的核心点先说结论：
- 可以直接在网页里添加/更新/删除多人面板。
- 不需要改 `.env`，也不需要重新构建镜像。
- 只要先设置好管理密码（`ADMIN_TOKEN` 或 `ADMIN_PASSWORD`）即可。

---

## 下载链接（按你的要求）

| 平台 | 下载链接 | 说明 |
|---|---|---|
| Windows | https://github.com/Monika-Dream/live-dashboard/releases | 原作者链接 |
| macOS | https://github.com/Monika-Dream/live-dashboard/releases | 原作者链接 |
| Android | https://github.com/nmb1337/live-dashboard/releases/latest/download/live-dashboard-android-agent.apk | 你的 Android App 链接 |

---

## 部署方式 1：Windows（PowerShell + Docker Desktop）

### 1. 先准备

1. 安装并启动 Docker Desktop。
2. 打开 PowerShell。
3. 进入项目目录：

```powershell
Set-Location D:\live-dashboard-main
```

### 2. 一键启动（推荐）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\deploy\windows-dockerdesktop-local.ps1
```

脚本会自动：
- 生成/修复 `.env`
- 生成设备 token
- 生成管理密码（`ADMIN_TOKEN`）
- 构建并启动容器

### 3. 验证

```powershell
Invoke-RestMethod http://127.0.0.1:3000/api/health
```

浏览器打开：
- http://127.0.0.1:3000

---

## 部署方式 2：Linux（Docker）

### 1. 准备 `.env`

```bash
cd /path/to/live-dashboard-main
cp .env.example .env
```

编辑 `.env`，至少改这几项：

```env
DEVICE_TOKEN_1=你的设备token:my-pc:My PC:linux
HASH_SECRET=你自己的随机字符串
ADMIN_TOKEN=你自己的管理密码
```

### 2. 启动

```bash
docker compose up -d --build
```

### 3. 验证

```bash
curl http://127.0.0.1:3000/api/health
```

---

## 如何设置“管理面板密码”

支持两种变量（设置一个即可）：
- `ADMIN_TOKEN`
- `ADMIN_PASSWORD`（优先级更高）

### 密码设置位置（最重要）

管理密码写在项目根目录的 `.env` 文件里：

- Windows 路径示例：`D:\live-dashboard-main\.env`
- Linux 路径示例：`/path/to/live-dashboard-main/.env`

也就是说，你只需要改 `.env`，不是改前端代码。

### 直接可用的设置方法

Windows（PowerShell）：

```powershell
Set-Location D:\live-dashboard-main
Copy-Item .env.example .env -Force

# 二选一（推荐只保留一个）
Add-Content .env "ADMIN_TOKEN=your_strong_password_here"
# 或
Add-Content .env "ADMIN_PASSWORD=your_strong_password_here"
```

Linux：

```bash
cd /path/to/live-dashboard-main
cp .env.example .env

# 二选一（推荐只保留一个）
echo 'ADMIN_TOKEN=your_strong_password_here' >> .env
# 或
echo 'ADMIN_PASSWORD=your_strong_password_here' >> .env
```

设置后重启容器：

```powershell
docker compose up -d --build
```

查看当前是否已设置：

Windows（PowerShell）：

```powershell
Get-Content .env | Select-String "ADMIN_TOKEN|ADMIN_PASSWORD"
```

Linux：

```bash
grep -E 'ADMIN_TOKEN|ADMIN_PASSWORD' .env
```

示例：

```env
ADMIN_TOKEN=your_strong_password_here
# 或
ADMIN_PASSWORD=your_strong_password_here
```

修改后重启：

```powershell
docker compose up -d --build
```

---

## 网页里管理多人面板（无需改 .env、无需重建）

打开首页后，在“多人面板管理”区域：

1. 输入管理密码（`ADMIN_TOKEN` 或 `ADMIN_PASSWORD` 的值）
2. 填写面板信息（ID、名称、URL、描述可选）
3. 点击“添加 / 更新面板”
4. 删除时点击“删除 xxx”

说明：
- 新增/更新/删除会立刻生效。
- 不需要修改 `.env`。
- 不需要重新构建 Docker 镜像。

---

## 常用命令

```powershell
# 查看日志
docker logs --tail 100 live_dashboard

# 重启容器
docker restart live_dashboard

# 重新构建并启动
docker compose up -d --build

# 停止
docker compose down
```

---

## 常见问题

### 1) 网页里添加/删除面板失败

先检查：
- 管理密码是否输入正确
- `.env` 是否有 `ADMIN_TOKEN` 或 `ADMIN_PASSWORD`

### 2) 看不到“多人面板管理”区域

执行：

```powershell
docker compose up -d --build
```

然后浏览器强制刷新（Ctrl+F5）。

### 3) 只有网址的人能不能乱改？

不能。

因为新增/更新/删除必须带管理密码（后端鉴权），没密码会返回 401。

---

## 许可证

MIT
