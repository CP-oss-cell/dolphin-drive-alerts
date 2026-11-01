#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  tests/java/android/os/Bundle.java \
  app/src/main/java/com/dolphin/oemhud/NavCue.java \
  app/src/main/java/com/dolphin/oemhud/MconHudQProtocol.java app/src/main/java/com/dolphin/oemhud/HudSettingsProtocol.java \
  tests/java/com/dolphin/oemhud/NavCueSelfTest.java \
  tests/java/com/dolphin/oemhud/MconHudQProtocolTest.java \
  tests/java/com/dolphin/oemhud/HudSettingsProtocolTest.java
java -cp "$out" com.dolphin.oemhud.NavCueSelfTest
java -cp "$out" com.dolphin.oemhud.MconHudQProtocolTest
java -cp "$out" com.dolphin.oemhud.HudSettingsProtocolTest
