#!/usr/bin/env bash
# 构建 Android APK（无需 Gradle，直接使用 Android SDK build-tools）
# DEBUG=1 bash android/build.sh <ver>  → 注入 android:debuggable="true"（仅供持久化自动化验证）
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
SDK="${ANDROID_SDK:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
AAPT="${AAPT:-/usr/bin/aapt}"          # Debian 原生 arm64 aapt v1
ZIPALIGN="${ZIPALIGN:-/usr/bin/zipalign}"
APKSIGNER="${APKSIGNER:-/usr/bin/apksigner}"
D8="${D8:-$BT/d8}"                     # Java 实现，跨架构可用
VERSION="${1:-0.0.2}"
OUT="artifacts/wangu-reader-v${VERSION}.apk"

if [ "${DEBUG:-0}" = "1" ]; then
  OUT="artifacts/wangu-reader-v${VERSION}-debug.apk"
else
  OUT="artifacts/wangu-reader-v${VERSION}.apk"
fi

echo "[1/6] 构建 Web 资源（Android 目标）"
npm run build:android >/dev/null
rm -rf android/assets && mkdir -p android/assets
cp -r dist-android android/assets/www

echo "[2/6] 编译资源"
cd android
rm -rf build && mkdir -p build/compiled build/classes build/dex
MANIFEST=AndroidManifest.xml
if [ "${DEBUG:-0}" = "1" ]; then
  # Debian aapt v1 要求 manifest 文件名必须是 AndroidManifest.xml → 临时替换再恢复
  cp AndroidManifest.xml build/AndroidManifest.orig.xml
  sed 's|<application|<application android:debuggable="true"|' AndroidManifest.xml > build/AndroidManifest.debuggen.xml
  mv build/AndroidManifest.debuggen.xml AndroidManifest.xml
  trap 'mv build/AndroidManifest.orig.xml AndroidManifest.xml' EXIT
fi
"$AAPT" package -f -M "$MANIFEST" -S res -A assets -I "$AJ" \
  --min-sdk-version 24 --target-sdk-version 34 -F build/base.apk
if [ "${DEBUG:-0}" = "1" ]; then
  mv build/AndroidManifest.orig.xml AndroidManifest.xml
  trap - EXIT
fi

echo "[3/6] 编译 Java"
javac -source 1.8 -target 1.8 -bootclasspath "$AJ" -encoding UTF-8 \
  -d build/classes $(find java -name '*.java')

echo "[4/6] 生成 dex"
"$D8" --lib "$AJ" --min-api 24 --output build/dex $(find build/classes -name '*.class')

echo "[5/6] 打包"
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk

echo "[6/6] 签名"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
    -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/$OUT" build/aligned.apk
cd "$ROOT"
echo "APK: $OUT ($(du -h "$OUT" | cut -f1))"
/usr/bin/aapt dump badging "$OUT" 2>/dev/null | head -3
