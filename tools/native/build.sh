#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(dirname "$(dirname "$(dirname "$(realpath "${BASH_SOURCE[0]}")")")")"
NATIVE_DIR="$REPO_ROOT/android-native"

# Select an isolated JDK17 without changing the user's global Java installation.
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /home/antony/opt/jdk-17.0.19+10 "$HOME/opt/jdk-17.0.20.1+1"; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo 'BLOCKED: set JAVA_HOME to an isolated JDK 17 installation.' >&2; exit 1
fi
if ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17\.'; then
  echo 'BLOCKED: native builds require JDK 17; refusing a silent JDK fallback.' >&2; exit 1
fi
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
if [ ! -f "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" ]; then
  echo 'BLOCKED: Android SDK platform 36 is required.' >&2; exit 1
fi
CMD="${1:-debug}"
if [ "$#" -gt 0 ]; then shift; fi
extra=()
# ARM hosts may supply a compatible host-native aapt2; never assume SDK binaries match the host.
if [ -n "${NATIVE_AAPT2:-}" ]; then extra+=("-Pandroid.aapt2FromMavenOverride=$NATIVE_AAPT2"); fi
case "$CMD" in
  test) tasks=(:core:test :app:testDebugUnitTest) ;;
  lint) tasks=(:app:lintDebug) ;;
  debug) tasks=(:core:test :app:testDebugUnitTest :app:assembleDebug) ;;
  release) tasks=(:core:test :app:assembleRelease :app:lintRelease) ;;
  dump-contracts) tasks=(:core:dumpContracts) ;;
  check) tasks=(:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug) ;;
  *) echo "Usage: $0 [debug|release|test|lint|check|dump-contracts] [Gradle flags]" >&2; exit 1 ;;
esac
exec bash "$NATIVE_DIR/gradlew" -p "$NATIVE_DIR" "${extra[@]}" "${tasks[@]}" "$@"
