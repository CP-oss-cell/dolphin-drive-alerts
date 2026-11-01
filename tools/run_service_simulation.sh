#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
find tests/simulated_android -name '*.java' -print > "$tmp/all.txt"
printf '%s\n' app/src/main/java/com/dolphin/oemhud/BridgeService.java app/src/main/java/com/dolphin/oemhud/NavCue.java app/src/main/java/com/dolphin/oemhud/MconHudQProtocol.java app/src/main/java/com/dolphin/oemhud/HudSettingsProtocol.java >> "$tmp/all.txt"
javac -encoding UTF-8 -d "$tmp/classes" @"$tmp/all.txt"
java -cp "$tmp/classes" com.dolphin.oemhud.BridgeFlowRuntimeSimulation
