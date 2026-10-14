#!/usr/bin/env bash
# 书源白名单（SourceRules）的纯 JVM 回归；不需要 Android SDK。
set -euo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" android/java/org/wanshu/reader/SourceRules.java test/android/SourceRulesTest.java
java -cp "$TMP" SourceRulesTest
