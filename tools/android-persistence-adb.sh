#!/usr/bin/env bash
# 专用测试设备：覆盖安装 debug APK，不清数据；会导入一份测试 TXT。
set -euo pipefail
cd "$(dirname "$0")/.."
command -v adb >/dev/null || { echo '需要 adb 和已授权设备' >&2; exit 1; }
VER="${1:-0.0.13}"
DEBUG=1 bash android/build.sh "$VER"
adb install -r "artifacts/wangu-reader-v${VER}-debug.apk"
node tools/android-persistence-adb.mjs
