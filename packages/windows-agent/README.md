# Windows Agent

用于 Windows 设备上报前台窗口到 Live Dashboard 后端。

## 特性

- 支持 `http://` 与 `https://` 后端地址
- 读取当前前台窗口进程名和窗口标题
- 按固定间隔调用 `POST /api/report`
- 使用 `Bearer <token>` 鉴权

## 使用方法

1. 安装 .NET 10 SDK（或更新版本）。
2. 复制配置文件：

```powershell
Copy-Item .\appsettings.example.json .\appsettings.json
```

3. 编辑 `appsettings.json`：

- `serverUrl`: 例如 `http://192.168.1.100:3000`
- `token`: 设备 token（从主面板管理里配置）
- `intervalSeconds`: 上报间隔（秒）

4. 运行：

```powershell
dotnet run --project .\WindowsAgent.csproj
```

5. 构建发布（可选）：

```powershell
dotnet publish .\WindowsAgent.csproj -c Release -r win-x64 --self-contained false
```

## 说明

- 该客户端只负责上报，设备名称和 token 映射由后端管理配置决定。
- 如果后端返回 `401 Unauthorized`，请检查 token 是否与主面板里配置一致。
