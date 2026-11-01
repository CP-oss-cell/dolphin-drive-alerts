#!/usr/bin/env python3
# Optional fallback for a PC with adb connected to the car and user-authorized.
import subprocess, sys
PKG="com.dolphin.oemhud"
LISTENER="com.dolphin.oemhud/com.dolphin.oemhud.NavNotificationService"
ACCESS="com.dolphin.oemhud/com.dolphin.oemhud.NavAccessibilityService"
def adb(*args):
    p=subprocess.run(["adb",*args],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print("adb "+" ".join(args)+" -> "+str(p.returncode)+"\n"+p.stdout[:700])
    if p.returncode:return None
    return p.stdout.strip()
if __name__=="__main__":
    if adb("devices") is None:sys.exit("adb 연결을 확인하세요")
    if adb("shell","pm","path",PKG) is None:sys.exit("앱 설치 후 실행하세요")
    adb("shell","pm","grant",PKG,"android.permission.WRITE_SECURE_SETTINGS")
    adb("shell","pm","grant",PKG,"android.permission.ACCESS_FINE_LOCATION")
    adb("shell","cmd","notification","allow_listener",LISTENER)
    if "--accessibility" in sys.argv:
        existing=adb("shell","settings","get","secure","enabled_accessibility_services")
        if existing is not None:
            entries=[] if existing=="null" else [x for x in existing.split(":") if x]
            if ACCESS not in entries:entries.append(ACCESS)
            merged=":".join(entries)
            adb("shell","settings","put","secure","enabled_accessibility_services",merged)
            adb("shell","settings","put","secure","accessibility_enabled","1")
    print("--- 최종 상태 ---")
    adb("shell","settings","get","secure","enabled_notification_listeners")
    adb("shell","settings","get","secure","enabled_accessibility_services")
    adb("shell","dumpsys","package",PKG)
    print("완료: 차량 정책에 의해 거부될 수 있습니다. TBT 실수신 성공과는 별개입니다.")
