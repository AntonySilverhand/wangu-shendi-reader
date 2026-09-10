#!/usr/bin/env bash
# 管理本地预览服务器（build 产物 + /api），避免误杀进程
# 用法: tools/reader-server.sh start|stop|restart|status [port]
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${2:-8787}"
PIDFILE="artifacts/reader-$PORT.pid"
LOG="artifacts/reader-$PORT.log"

status() {
  if [[ -f "$PIDFILE" ]]; then
    local pid
    pid=$(cat "$PIDFILE")
    if kill -0 "$pid" 2>/dev/null; then
      echo "running pid=$pid port=$PORT log=$LOG"
      return 0
    fi
  fi
  echo "stopped"
  return 1
}

start() {
  if status >/dev/null 2>&1; then
    echo "already running"
    exit 0
  fi
  setsid nohup node --experimental-strip-types src/server/dev.ts --port "$PORT" \
    >"$LOG" 2>&1 </dev/null &
  echo $! >"$PIDFILE"
  sleep 1.5
  echo "started pid=$(cat "$PIDFILE") port=$PORT log=$LOG"
}

stop() {
  if [[ -f "$PIDFILE" ]]; then
    local pid
    pid=$(cat "$PIDFILE")
    kill "$pid" 2>/dev/null || true
    rm -f "$PIDFILE"
  fi
  echo "stopped"
}

case "${1:-status}" in
  start) start ;;
  stop) stop ;;
  restart) stop; sleep 1; start ;;
  status) status ;;
  *) echo "usage: $0 start|stop|restart|status [port]"; exit 2 ;;
esac
