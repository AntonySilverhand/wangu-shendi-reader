#!/usr/bin/env bash
# 一次命令内完成：构建 → 启动本地服务 → 聚焦验证 → 关闭服务
set -euo pipefail
cd "$(dirname "$0")/.."
npm run build >/dev/null
node --experimental-strip-types src/server/dev.ts --port 8787 >artifacts/tmp-server.log 2>&1 &
SPID=$!
trap 'kill "$SPID" 2>/dev/null || true' EXIT
sleep 1.5
node test/verify-final.mjs "$@"
