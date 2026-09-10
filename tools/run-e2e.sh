#!/usr/bin/env bash
# 后台运行 E2E 并立即返回；结果写入 /tmp/wangu-e2e.log
set -euo pipefail
cd "$(dirname "$0")/.."
LOG=artifacts/e2e.log
: >"$LOG"
setsid nohup node test/e2e.mjs >"$LOG" 2>&1 </dev/null &
echo $! >artifacts/e2e.pid
sleep 2
echo "e2e started pid=$(cat /tmp/wangu-e2e.pid) log=$LOG"
