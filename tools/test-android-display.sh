#!/usr/bin/env bash
# 无设备的原生 IME 几何回归；不等同于 Android 真机验证。
set -euo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" android/java/org/wanshu/reader/DisplayGeometry.java test/android/DisplayGeometryTest.java
java -cp "$TMP" DisplayGeometryTest
