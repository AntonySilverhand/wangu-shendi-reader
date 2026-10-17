#!/usr/bin/env bash
# 构建 Android APK（无需 Gradle，直接使用 Android SDK build-tools）
# DEBUG=1 bash android/build.sh <ver>  → 注入 android:debuggable="true"（仅供持久化自动化验证）
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
SDK="${ANDROID_SDK:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
# aapt/zipalign/apksigner：优先系统软件包（Debian arm64 原生，SDK 里的是 x86_64 二进制），
# 没有时用 SDK build-tools（x86_64，tools/setup-android-sdk.sh 安装）
tool() { if [ -x "/usr/bin/$1" ]; then echo "/usr/bin/$1"; else echo "$BT/$1"; fi; }
AAPT="${AAPT:-$(tool aapt)}"
ZIPALIGN="${ZIPALIGN:-$(tool zipalign)}"
APKSIGNER="${APKSIGNER:-$(tool apksigner)}"
D8="${D8:-$BT/d8}"                     # Java 实现，跨架构可用
for f in "$AJ" "$AAPT" "$ZIPALIGN" "$APKSIGNER" "$D8"; do
  [ -e "$f" ] || { echo "缺少 $f：先运行 bash tools/setup-android-sdk.sh" >&2; exit 1; }
done
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
(cd build/dex && "$AAPT" add -k ../unsigned.apk classes.dex >/dev/null)
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk

echo "[6/6] 签名"
# 发布签名：ANDROID_KEYSTORE 指向固定密钥（CI 由仓库 secret 解出）；未指定时用本机 debug.keystore。
# 换密钥会导致无法覆盖安装，发布版务必用同一把密钥。
KS="${ANDROID_KEYSTORE:-debug.keystore}"
KS_PASS="${ANDROID_KEYSTORE_PASS:-android}"
if [ ! -f "$KS" ]; then
  if [ -n "${ANDROID_KEYSTORE:-}" ]; then echo "签名密钥不存在：$KS" >&2; exit 1; fi
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -alias androiddebugkey \
    -keypass "$KS_PASS" -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$ROOT/$OUT" build/aligned.apk
cd "$ROOT"
echo "APK: $OUT ($(du -h "$OUT" | cut -f1))"
"$AAPT" dump badging "$OUT" 2>/dev/null | head -3 || true
