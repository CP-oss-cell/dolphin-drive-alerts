#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/create_dashboard_compile_stubs.py build/dashboard_compile_stubs
mkdir -p build/dashboard_ui_classes
javac -encoding UTF-8 -d build/dashboard_ui_classes \
  $(find build/dashboard_compile_stubs -name '*.java' | sort) \
  app/src/main/java/com/dolphin/oemhud/MainActivity.java
echo 'PASS DASHBOARD Java source type-check against offline Android UI stubs (not android.jar)'
