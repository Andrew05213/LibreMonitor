#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT/.toolchain/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.toolchain/gradle-cache}"
if [[ ! -x "$JAVA_HOME/bin/java" || ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
  printf '%s\n' 'JDK17 / SDK35 missing. Read docs/BUILDING.md first.' >&2
  exit 2
fi
AAPT2="${AAPT2:-}"
if [[ -z "$AAPT2" && -f "$ROOT/.toolchain/aapt2-path" ]]; then
  IFS= read -r AAPT2 < "$ROOT/.toolchain/aapt2-path"
fi
if [[ -z "$AAPT2" || ! -x "$AAPT2" ]]; then
  printf '%s\n' 'A working ARM64 aapt2 is required. Read docs/BUILDING.md.' >&2
  exit 2
fi
GRADLE="$ROOT/.toolchain/gradle-8.9/bin/gradle"
if [[ ! -x "$GRADLE" ]]; then GRADLE="$ROOT/gradlew"; fi
if [[ "$#" == 0 ]]; then set -- :core:test :app:assembleDebug :app:lintDebug; fi
exec "$GRADLE" -p "$ROOT" --no-daemon --max-workers=2 \
  "-Pandroid.aapt2FromMavenOverride=$AAPT2" "$@"
