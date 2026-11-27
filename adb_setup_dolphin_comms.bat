@echo off
chcp 65001 >nul
set PKG=com.dolphin.comms
set APK=%~1

echo ========================================================
echo   DolphinComms BYD DiLink ADB 원클릭 권한 설정 스크립트
echo ========================================================

if not "%APK%"=="" (
    echo [1/6] APK 설치 중 (-r -g)...
    adb install -r -g "%APK%"
) else (
    echo [1/6] APK 경로 생략됨 - 이미 설치된 %PKG%에 권한만 부여합니다.
)

echo [2/6] 런타임 + Android 10+ 제한 권한(통화기록/문자/MMS/BT) 부여...
for %%P in (
    android.permission.CALL_PHONE
    android.permission.READ_PHONE_STATE
    android.permission.ANSWER_PHONE_CALLS
    android.permission.READ_CONTACTS
    android.permission.READ_CALL_LOG
    android.permission.WRITE_CALL_LOG
    android.permission.BLUETOOTH_CONNECT
    android.permission.READ_SMS
    android.permission.RECEIVE_SMS
    android.permission.SEND_SMS
    android.permission.RECEIVE_MMS
    android.permission.RECEIVE_WAP_PUSH
    android.permission.POST_NOTIFICATIONS
) do (
    adb shell pm grant %PKG% %%P 2>nul
)

echo [3/6] 작은 수신화면 오버레이 & SMS 쓰기 AppOps 허용...
adb shell appops set %PKG% SYSTEM_ALERT_WINDOW allow
adb shell appops set %PKG% WRITE_SMS allow
adb shell appops set %PKG% READ_CALL_LOG allow
adb shell appops set %PKG% READ_SMS allow

echo [4/6] 기본 전화 앱(ROLE_DIALER) & 기본 문자 앱(ROLE_SMS) 지정...
adb shell cmd role add-role-holder android.app.role.DIALER %PKG%
adb shell cmd role add-role-holder android.app.role.SMS %PKG%

echo [5/6] 시스템 재난문자(CBS) 알림 폴백 리스너 활성화...
adb shell cmd notification allow_listener %PKG%/com.dolphin.comms.sms.EmergencyNotificationListener

echo [6/6] 백그라운드 강제종료 방지 (배터리 최적화 제외)...
adb shell dumpsys deviceidle whitelist +%PKG%

echo.
echo 완료! 차량에서 DolphinComms를 실행해 상태 요약을 확인하세요.
pause
