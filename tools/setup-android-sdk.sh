#!/usr/bin/env bash
# 准备打 Android 壳 APK 所需的最小环境（与 docs/ci/release-apk.yml 用同一组组件）：
#   cmdline-tools（sdkmanager）+ build-tools;34.0.0（d8/aapt/zipalign/apksigner）+ platforms;android-34（android.jar）
# 用法：bash tools/setup-android-sdk.sh          # 装到 ${ANDROID_SDK:-$HOME/android-sdk}
# 之后：bash android/build.sh <版本号>
#
# 架构说明：build-tools 里的 aapt/zipalign 是 x86_64 二进制。arm64 机器（如 Debian arm64）上
# 只用 SDK 里的 d8（Java）与 android.jar，aapt/zipalign/apksigner 改用发行版软件包
# （sudo apt install aapt zipalign apksigner），android/build.sh 会优先使用 /usr/bin 下的版本。
# 需要 JDK（javac/keytool，17 或 21 均可）与 unzip/curl。
set -euo pipefail

SDK="${ANDROID_SDK:-$HOME/android-sdk}"
CMDLINE_ZIP="commandlinetools-linux-16111833_latest.zip"
CMDLINE_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
PACKAGES=("build-tools;34.0.0" "platforms;android-34")

need() { command -v "$1" >/dev/null 2>&1 || { echo "缺少命令：$1（$2）" >&2; exit 1; }; }
need java "请先安装 JDK 17+，如 sudo apt install openjdk-17-jdk-headless"
need javac "请先安装 JDK 17+（需要 javac）"
need keytool "JDK 自带"
need curl "sudo apt install curl"
need unzip "sudo apt install unzip"

mkdir -p "$SDK"
BIN="$SDK/cmdline-tools/latest/bin"
if [ ! -x "$BIN/sdkmanager" ]; then
  echo "[1/3] 下载 Android 命令行工具"
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  curl -fsSL --retry 3 -o "$TMP/$CMDLINE_ZIP" "https://dl.google.com/android/repository/$CMDLINE_ZIP"
  echo "$CMDLINE_SHA1  $TMP/$CMDLINE_ZIP" | sha1sum -c - >/dev/null || { echo "命令行工具校验失败" >&2; exit 1; }
  unzip -q "$TMP/$CMDLINE_ZIP" -d "$TMP/unpacked"
  rm -rf "$SDK/cmdline-tools/latest"
  mkdir -p "$SDK/cmdline-tools"
  mv "$TMP/unpacked/cmdline-tools" "$SDK/cmdline-tools/latest"
else
  echo "[1/3] 已有命令行工具：$BIN"
fi

echo "[2/3] 安装 ${PACKAGES[*]}（即表示接受 Android SDK 许可：https://developer.android.com/studio/terms）"
if [ -x "$BIN/android" ]; then
  # 新版命令行工具里 sdkmanager 只是转调 Android CLI（默认收集使用统计）：直接调用并关闭统计
  "$BIN/android" --no-metrics --sdk="$SDK" sdk install "${PACKAGES[@]}" >/dev/null
else
  yes | "$BIN/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
  "$BIN/sdkmanager" --sdk_root="$SDK" "${PACKAGES[@]}" >/dev/null
fi

echo "[3/3] 检查"
BT="$SDK/build-tools/34.0.0"
test -f "$SDK/platforms/android-34/android.jar" || { echo "缺少 android.jar" >&2; exit 1; }
"$BT/d8" --version >/dev/null 2>&1 || { echo "d8 无法运行" >&2; exit 1; }
ARCH="$(uname -m)"
if [ "$ARCH" = "x86_64" ]; then
  "$BT/aapt" version >/dev/null && "$BT/zipalign" 2>&1 | head -1 >/dev/null && "$BT/apksigner" --version >/dev/null
  echo "OK：SDK 在 $SDK（x86_64：aapt/zipalign/apksigner 用 build-tools 34.0.0）"
else
  missing=()
  for tool in aapt zipalign apksigner; do command -v "$tool" >/dev/null 2>&1 || missing+=("$tool"); done
  if [ ${#missing[@]} -gt 0 ]; then
    echo "SDK 已就绪（$ARCH）。还需安装发行版工具：sudo apt install ${missing[*]}" >&2
    exit 1
  fi
  echo "OK：SDK 在 $SDK（$ARCH：aapt/zipalign/apksigner 用系统软件包）"
fi
echo "下一步：bash android/build.sh <版本号>"
