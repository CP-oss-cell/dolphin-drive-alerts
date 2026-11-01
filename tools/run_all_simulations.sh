#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/preflight.py
bash tools/run_jvm_tests.sh
bash tools/run_ble_simulation.sh
bash tools/run_integrated_simulation.sh
bash tools/run_service_simulation.sh
bash tools/run_adb_kotlin_syntax.sh
printf '\nALL AVAILABLE LOCAL SIMULATIONS PASS. ANDROID APK ASSEMBLE / REAL CAR / HUD HARDWARE ARE NOT VERIFIED.\n'
