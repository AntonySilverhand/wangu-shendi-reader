#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
NATIVE_DIR="$REPO_ROOT/android-native"

export JAVA_HOME="${JAVA_HOME:-/home/antony/opt/jdk-17.0.19+10}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

if [ ! -d "$JAVA_HOME" ]; then
  echo "Error: JDK 17 not found at $JAVA_HOME" >&2
  exit 1
fi

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
  echo "Error: Android SDK not found at $ANDROID_SDK_ROOT" >&2
  exit 1
fi

CMD="${1:-debug}"

cd "$NATIVE_DIR"

case "$CMD" in
  test)
    echo "==> Running native unit tests..."
    ./gradlew :core:test :app:testDebugUnitTest
    ;;
  lint)
    echo "==> Running native lint..."
    ./gradlew :app:lintDebug
    ;;
  debug)
    echo "==> Running native tests and assembling debug APK..."
    ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
    ;;
  release)
    echo "==> Assembling release APK and running release lint..."
    ./gradlew :core:test :app:assembleRelease :app:lintRelease
    ;;
  dump-contracts)
    echo "==> Dumping native source contracts to JSON..."
    ./gradlew :core:dumpContracts
    ;;
  check)
    echo "==> Running full verification (tests + lint + assembleDebug)..."
    ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
    ;;
  *)
    echo "Usage: $0 [debug|release|test|lint|check|dump-contracts]" >&2
    exit 1
    ;;
esac
