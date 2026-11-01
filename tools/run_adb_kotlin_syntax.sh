#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v kotlinc >/dev/null 2>&1; then echo 'SKIP adb Kotlin syntax: kotlinc not installed'; exit 0; fi
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
find tests/adb_compile_stubs -name '*.java' > "$tmp/stubs.txt"
javac --release 17 -encoding UTF-8 -d "$tmp/classes" @"$tmp/stubs.txt"
kotlinc -jvm-target 17 -classpath "$tmp/classes" -d "$tmp/classes" \
 app/src/main/java/com/dolphin/oemhud/adb/NativeAdbClient.kt \
 app/src/main/java/com/dolphin/oemhud/adb/HudAdbPermissions.kt
printf 'PASS ADB Kotlin source type/syntax check against OFFLINE Android/Dadb stubs (NOT an Android SDK build)\n'
