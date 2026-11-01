#!/usr/bin/env python3
"""Checks no external AutoSplit dependency and exercises REAL Kotlin split geometry code."""
from pathlib import Path
import subprocess, tempfile, shutil, re, sys
root=Path(__file__).resolve().parent.parent
base=root/'app/src/main/java/com/byd/dolphin/drivealerts/automation'
read=lambda name:(base/name).read_text(encoding='utf-8')
ui=read('SplitProfilesActivity.kt'); engine=read('VehicleSplitEngine.kt'); restore=read('SplitRecoveryService.kt')
seq=read('AutoLaunchSequenceService.kt'); settings=read('AutoLaunchSettingsActivity.kt')
layout=(root/'app/build.gradle.kts').read_text()
checks={
 'external delegate source removed':not (base/'AutoSplitBridge.kt').exists(),
 'no external package in active source': not any('com.sspark.bydautosplit' in p.read_text(errors='replace') for p in (root/'app/src/main').rglob('*.kt')),
 'direct internal split screen': 'VehicleSplitEngine.launch(applicationContext, p)' in ui,
 'direct internal shortcut': 'class SplitShortcutActivity' in ui and 'VehicleSplitEngine.launch(applicationContext, p)' in ui,
 'saved profiles not erased': 'FeatureProfiles.saveSplits' in ui,
 'two-app selector': 'firstPackage' in ui and 'secondPackage' in ui,
 'ratio slider save and apply': 'seek.progress+10' in ui and 'VehicleSplitEngine.applyRatio(appContext,p.primaryPercent)' in ui,
 'named shortcuts': 'requestPinShortcut(shortcut,null)' in ui,
 'explicit ADB readiness': 'AuthorizedAdbRuntime.health' in ui,
 'in-app primary task launch': 'PRIMARY_GHOST' in engine and 'PRIMARY_MOVE' in engine,
 'in-app secondary task launch': 'SECONDARY_START' in engine,
 'no blind success of two-app ratio': 'TWO_APPS_VERIFIED_RATIO_NOT_VERIFIED' in engine,
 'docked stack resize 8 args': 'resize-docked-stack' in (base/'SplitGeometry.kt').read_text(),
 'ratio post-readback': 'RATIO_OBSERVED' in engine and 'SPLIT_POST_RATIO_TASKS' in engine,
 'camera protected on split': '카메라 화면 표시 중' in engine,
 'camera protected on resize': '카메라 화면 표시 중: 비율 변경 보류' in engine,
 'camera restore actively running': 'Thread({watch(p,local)}' in restore and 'DISABLED_DELEGATED_TO_AUTOSPLIT' not in restore,
 'camera restore retry bounded': 'attempts<3' in restore,
 'user-chosen ignition profile runs internally': 'val result=VehicleSplitEngine.launch(this,profile)' in seq,
 'camera protected on ignition': 'SPLIT_SKIPPED_CAMERA_FOREGROUND' in seq,
 'saved split profiles in ignition picker': 'AppSelection.App("분할 프로필:' in settings,
 'no autosplit installation required UI': 'AutoSplit 설치는 필요하지 않습니다' in ui,
 'same package and versionCode': 'applicationId = "com.byd.dolphin.drivealerts"' in layout and 'versionCode = 63' in layout,
 '6-card menu still present': 'SplitProfilesActivity::class.java' in (root/'app/src/main/java/com/byd/dolphin/drivealerts/ui/MainActivity.kt').read_text(),
}
for name,ok in checks.items():
 print(('PASS' if ok else 'FAIL')+': '+name)
if not all(checks.values()):sys.exit(1)
if shutil.which('kotlinc'):
 with tempfile.TemporaryDirectory() as td:
  fixture=Path(td)/'GeometryGate.kt'
  fixture.write_text('''package com.byd.dolphin.drivealerts.automation
fun main() {
  val a = "Stack id=3 bounds=[0,0][632,720] mWindowingMode=split-screen-primary\\n"+
          "Stack id=4 bounds=[648,0][1280,720] mWindowingMode=split-screen-secondary\\n"
  val observed=ReferenceAutoSplitRatio.parse(a) ?: error("reference parser")
  check(SplitGeometry.percent(observed)==50)
  val command=SplitGeometry.resize(observed, 70) ?: error("resize")
  check(command.command.startsWith("am stack resize-docked-stack 0 0 885 720")) { command }
  check(command.command.split(" ").size==11) { command.command }
  val portrait=SplitGeometry.PairBounds(SplitGeometry.Rect(0,0,720,310),SplitGeometry.Rect(0,326,720,1280))
  check(SplitGeometry.percent(portrait)==25)
  val next=SplitGeometry.resize(portrait,40) ?: error("portrait ratio")
  check(next.axis=="Y")
  check(next.command.contains("0 0 720 506")) { next.command }
  val overlapping=SplitGeometry.PairBounds(SplitGeometry.Rect(0,0,700,720),SplitGeometry.Rect(650,0,1280,720))
  check(SplitGeometry.resize(overlapping,55)==null)
  println("PASS: Kotlin split ratio horizontal / vertical / invalid overlaps")
}''')
  out=Path(td)/'test.jar'
  cmd=['kotlinc',str(base/'SplitGeometry.kt'),str(base/'ReferenceAutoSplitRatio.kt'),str(fixture),'-include-runtime','-d',str(out)]
  p=subprocess.run(cmd,capture_output=True,text=True,timeout=90)
  if p.returncode:print(p.stdout,p.stderr);sys.exit(p.returncode)
  p=subprocess.run(['java','-jar',str(out)],capture_output=True,text=True,timeout=25)
  print(p.stdout,p.stderr)
  if p.returncode:sys.exit(p.returncode)
else:print('SKIP Kotlin geometry fixture (kotlinc unavailable)')
print('ALL v0.17.37 internal split gates PASS')
