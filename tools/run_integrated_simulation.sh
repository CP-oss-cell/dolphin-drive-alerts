#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
find tests/ble_simulated_android -name '*.java' ! -path '*/com/dolphin/oemhud/BleRuntimeTest.java' ! -path '*/com/dolphin/oemhud/BridgeLog.java' -print > "$tmp/inputs"
find tests/simulated_android/android/app -name '*.java' -print >> "$tmp/inputs"
printf '%s\n' tests/simulated_android/android/os/Build.java tests/simulated_android/android/os/IBinder.java tests/simulated_android/android/R.java >> "$tmp/inputs"
find tests/combined_android -name '*.java' -print >> "$tmp/inputs"
for f in BridgeService HudConnector NavCue MconHudQProtocol HudSettingsProtocol; do echo "app/src/main/java/com/dolphin/oemhud/$f.java" >> "$tmp/inputs"; done
javac -encoding UTF-8 -d "$tmp/classes" @"$tmp/inputs"
java -cp "$tmp/classes" com.dolphin.oemhud.EndToEndRuntimeTest
