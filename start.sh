#!/usr/bin/env bash
# Live Dashboard — 一键启动脚本（macOS 本地开发）
set -euo pipefail

# ── 颜色 ──────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${BLUE}[INFO]${RESET} $*"; }
success() { echo -e "${GREEN}[OK]${RESET}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET} $*"; }
die()     { echo -e "${RED}[ERR]${RESET}  $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "\n${BOLD}🌸 Live Dashboard — 本地启动${RESET}\n"

# ── 检查依赖 ──────────────────────────────────────────────────────────────────
info "检查依赖..."

command -v bun   &>/dev/null || die "未找到 bun，请先安装：https://bun.sh"
command -v python3 &>/dev/null || die "未找到 python3，请先安装 Python 3.10+"

PYTHON=python3

# ── 首次配置：生成 .env ───────────────────────────────────────────────────────
ENV_FILE="packages/backend/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  info "首次运行，自动生成配置..."

  TOKEN=$(openssl rand -hex 16)
  HASH_SECRET=$(openssl rand -hex 32)
  DEVICE_ID="my-mac"
  DEVICE_NAME="My Mac"

  cat > "$ENV_FILE" <<EOF
# 设备令牌：token:device_id:device_name:platform
DEVICE_TOKEN_1=${TOKEN}:${DEVICE_ID}:${DEVICE_NAME}:macos

# HMAC 密钥（用于窗口标题哈希去重）
HASH_SECRET=${HASH_SECRET}

# 可选配置
# PORT=3000
# DB_PATH=./live-dashboard.db
EOF

  success "已生成 $ENV_FILE（token: ${TOKEN}）"
else
  success "$ENV_FILE 已存在，跳过"
fi

# ── 读取 token（用于 agent config）────────────────────────────────────────────
TOKEN=$(grep "^DEVICE_TOKEN_1=" "$ENV_FILE" | head -1 | cut -d= -f2 | cut -d: -f1)
[[ -z "$TOKEN" ]] && die "无法从 .env 读取 DEVICE_TOKEN_1"

# ── 首次配置：生成 agent config.json ─────────────────────────────────────────
AGENT_CONFIG="agents/macos/config.json"
if [[ ! -f "$AGENT_CONFIG" ]]; then
  cat > "$AGENT_CONFIG" <<EOF
{
  "server_url": "http://localhost:3000",
  "token": "${TOKEN}",
  "interval_seconds": 5,
  "heartbeat_seconds": 60
}
EOF
  success "已生成 $AGENT_CONFIG"
else
  success "$AGENT_CONFIG 已存在，跳过"
fi

# ── 安装后端依赖 ──────────────────────────────────────────────────────────────
info "安装后端依赖..."
(cd packages/backend && bun install --frozen-lockfile 2>&1 | tail -3) || \
(cd packages/backend && bun install 2>&1 | tail -3)
success "后端依赖就绪"

# ── 安装前端依赖 ──────────────────────────────────────────────────────────────
info "安装前端依赖..."
(cd packages/frontend && bun install --frozen-lockfile 2>&1 | tail -3) || \
(cd packages/frontend && bun install 2>&1 | tail -3)
success "前端依赖就绪"

# ── 构建前端（如果 public 目录为空）─────────────────────────────────────────
PUBLIC_DIR="packages/backend/public"
NEED_BUILD=false
if [[ ! -d "$PUBLIC_DIR" ]] || [[ -z "$(ls -A "$PUBLIC_DIR" 2>/dev/null)" ]]; then
  NEED_BUILD=true
fi

if $NEED_BUILD; then
  info "构建前端（首次构建约需 1 分钟）..."
  (cd packages/frontend && bun run build 2>&1 | tail -5)
  mkdir -p "$PUBLIC_DIR"
  cp -r packages/frontend/out/. "$PUBLIC_DIR/"
  success "前端构建完成"
else
  success "前端已构建，跳过"
fi

# ── 安装 Python 依赖 ──────────────────────────────────────────────────────────
info "检查 Python 依赖..."
if ! $PYTHON -c "import psutil, requests" &>/dev/null; then
  info "安装 psutil / requests..."
  $PYTHON -m pip install -q -r agents/macos/requirements.txt
fi
success "Python 依赖就绪"

# ── 启动后端 ──────────────────────────────────────────────────────────────────
echo ""
info "启动后端服务..."
(cd packages/backend && bun run src/index.ts) &
BACKEND_PID=$!

# 等待后端就绪
for i in $(seq 1 20); do
  sleep 0.5
  if curl -sf http://localhost:3000/api/health &>/dev/null; then
    success "后端已就绪 → http://localhost:3000"
    break
  fi
  if [[ $i -eq 20 ]]; then
    die "后端启动超时，请检查日志"
  fi
done

# ── 启动 macOS Agent ──────────────────────────────────────────────────────────
info "启动 macOS Agent..."
echo -e "${YELLOW}[提示]${RESET} 首次运行可能弹出辅助功能权限请求，请在系统设置中允许"
$PYTHON agents/macos/agent.py &
AGENT_PID=$!
success "Agent 已启动（PID: $AGENT_PID）"

# ── 打开浏览器 ────────────────────────────────────────────────────────────────
sleep 1
open "http://localhost:3000" 2>/dev/null || true

echo ""
echo -e "${GREEN}${BOLD}✅ 启动完成！${RESET}"
echo -e "   仪表盘：${BLUE}http://localhost:3000${RESET}"
echo -e "   按 ${BOLD}Ctrl+C${RESET} 停止所有服务\n"

# ── 优雅退出 ──────────────────────────────────────────────────────────────────
cleanup() {
  echo ""
  info "正在关闭..."
  kill $BACKEND_PID 2>/dev/null || true
  kill $AGENT_PID   2>/dev/null || true
  wait $BACKEND_PID $AGENT_PID 2>/dev/null || true
  success "已停止"
  exit 0
}
trap cleanup INT TERM

# 保持脚本运行并转发后端日志
wait $BACKEND_PID
