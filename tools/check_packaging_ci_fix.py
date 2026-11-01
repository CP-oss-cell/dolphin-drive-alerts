#!/usr/bin/env python3
"""Offline guard for the GitHub Actions 2026-11-01 mergeDebugJavaResource failure.

This is a static regression check, not a substitute for :app:assembleDebug.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
build = (root / 'app/build.gradle').read_text(encoding='utf-8')
workflow = (root / '.github/workflows/build-oem-hud-bridge.yml').read_text(encoding='utf-8')
assert re.search(r'packaging\s*\{\s*resources\s*\{', build), 'Missing AGP 8.x packaging resources block'
assert 'pickFirsts' in build, 'No duplicate resource handling'
for notice in ('/META-INF/LICENSE.md', '/META-INF/LICENSE-notice.md', '/META-INF/NOTICE.md'):
    assert notice in build, f'Missing guard for {notice}'
assert 'META-INF/**' not in build, 'Too broad: would interfere with META-INF/services'
assert 'gradle --no-daemon --stacktrace :app:assembleDebug' in workflow, 'APK build task missing'
assert 'test -s app/build/outputs/apk/debug/app-debug.apk' in workflow, 'APK artifact confirmation missing'
print('CI resource packaging regression guard: PASS')
