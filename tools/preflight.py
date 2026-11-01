#!/usr/bin/env python3
"""Source/package preflight, not a replacement for Android assembleDebug."""
from pathlib import Path
import re,sys,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
checks=[]
def check(name,condition):
    checks.append((name,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' PREFLIGHT '+name)
manifest=root/'app/src/main/AndroidManifest.xml'
xml=ET.parse(manifest).getroot()
ns='{http://schemas.android.com/apk/res/android}'
pkg='com.dolphin.oemhud'
base=root/'app/src/main/java/com/dolphin/oemhud'
code=lambda n:(base/n).read_text(encoding='utf-8')
check('Manifest XML parse',xml.tag=='manifest')
permissions={x.attrib.get(ns+'name') for x in xml.findall('uses-permission')}
for permission in ['android.permission.INTERNET','android.permission.FOREGROUND_SERVICE','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.BLUETOOTH','android.permission.BLUETOOTH_ADMIN','android.permission.ACCESS_FINE_LOCATION','android.permission.WRITE_SECURE_SETTINGS']:
    check('Manifest permission '+permission,permission in permissions)
for tag,klass in [('activity','.MainActivity'),('service','.BridgeService'),('service','.NavAccessibilityService'),('service','.NavNotificationService'),('receiver','.BootReceiver')]:
    present=any(x.attrib.get(ns+'name')==klass for x in xml.findall('.//'+tag))
    check('Manifest component '+klass,present)
check('Android minSDK 29',"minSdk 29" in (root/'app/build.gradle').read_text())
check('Android targetSDK 29',"targetSdk 29" in (root/'app/build.gradle').read_text())
check('App version 0.9',"versionCode 10" in (root/'app/build.gradle').read_text())
check('AutoNavi broadcast registered','AUTONAVI_STANDARD_BROADCAST_SEND' in code('BridgeService.java'))
check('TBT accepted only while enabled','getBoolean("enabled",false)' in code('BridgeService.java'))
check('Off state blocks TBT history','getBoolean("enabled",false)' in code('BridgeLog.java'))
check('MCon known packet prefix','0x19' in code('MconHudQProtocol.java') and '0x4d' in code('MconHudQProtocol.java'))
check('BLE FFF2 characteristic','0000fff2-0000-1000-8000-00805f9b34fb' in code('HudConnector.java'))
check('Monotonic BLE TTL','android.os.SystemClock.elapsedRealtime()' in code('HudConnector.java'))
check('CCCD serialization gate','descriptorPending' in code('HudConnector.java') and 'FFF1 알림 설정 응답 시간 초과' in code('HudConnector.java'))
check('Old GATT discarded on ACK timeout','GATT ACK 없음 → 새 연결에서 재전송' in code('HudConnector.java'))
check('ADB CAS gate','compareAndSet(false,true)' in code('adb/HudAdbPermissions.kt'))
check('ADB connection keeps persistent RSA files','adbkey.pub' in code('adb/NativeAdbClient.kt'))
check('No root request in runtime','su -c' not in '\n'.join(p.read_text(errors='ignore') for p in base.rglob('*') if p.is_file()))
check('CI workflow exists',(root/'.github/workflows/build-oem-hud-bridge.yml').is_file())
check('HUD setting validated protocol', 'isSettingsFrame' in code('HudSettingsProtocol.java'))
check('Camera buzzer / brightness controls', 'ACTION_BUZZER' in code('MainActivity.java') and 'ACTION_BRIGHTNESS' in code('MainActivity.java'))

# v0.9 dashboard contract: one owner for each feature, no legacy duplicate action menus.
ui=code('MainActivity.java')
check('Dashboard single owner for buzzer + brightness',ui.count('경보음·밝기 함께 저장 및 전송')==1)
check('Dashboard ADB single setup action',ui.count('ADB 연결·권한 적용')>=1 and 'HudAdbPermissions.connect' in ui and 'HudAdbPermissions.apply(this,false' in ui)
check('Dashboard operational start/stop single toggle','toggleRunning()' in ui and '운행 연동' in ui)
check('Dashboard advanced diagnostics collapsed by default','advancedContent.setVisibility(View.GONE)' in ui)
check('Dashboard v0.9 branding','순정 내비 HUD 대시보드' in ui)
fail=[n for n,good in checks if not good]
print('DASHBOARD PREFLIGHT SUMMARY',len(checks)-len(fail),'/',len(checks),'PASS')
if fail:sys.exit(1)
