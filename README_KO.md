# Dolphin OEM HUD Bridge v0.9 — 대시보드 통합판

이 프로젝트는 **BYD 돌핀 순정 내비 길안내(TBT) → 아리아/TPLAY HUD Q** 연결을 위한 독립 Android 앱 전체 소스입니다. 기존 MCON K를 코드에 포함하지 않으며 차량 ECU에 명령을 송신하지 않습니다.

## v0.9 사용자 화면 개편

복잡한 번호별 버튼 나열을 없애고 **한 화면의 대시보드**로 정리했습니다.

| 화면 영역 | 담당 기능 | 메뉴 정리 |
|---|---|---|
| 상단 상태 | HUD BLE 상태, 순정 TBT 방송 수신 횟수, 마지막 ADB 결과 | 조회 전용 |
| 운행 연동 | 수신 시작/중지, 시동 후 자동 실행 설정 | 버튼 1개로 통합 |
| HUD 연결 | 기기 검색, 페어링 설정, 연결/자동 재연결, 연결 해제 | 중복 검색/저장 경로 제거 |
| 경보음·밝기 | 부저 ON/OFF, 3단계 음량, 주간/야간 3단계 밝기, 자동/고정 | **저장·전송 버튼 1개로 통합** |
| 권한 관리 | ADB 연결·RSA 인증 후 필수 권한 적용 | 승인이 성공한 경우에만 권한 단계로 이어짐 |
| 고급 설정·진단 | 내비 패키지 지정, 선택적 접근성, 권한 페이지, ZIP 저장 | 기본 상태에서 접힘 |

- 기존 Preferences 저장 키, 프로토콜, ADB 자체 RSA 키 및 송신·재연결 로직은 유지했습니다.
- v0.8 설정값은 동일한 앱 패키지로 업데이트하면 이어받도록 설계되어 있습니다. 실제 업데이트 검증은 미완료입니다.
- 경보음은 HUD **전체 부저**를 제어하며 카메라 경보 전용 독립 음량이 아닙니다.
- 자동 밝기는 **HUD 장치 자체 주야간 기능에 지정값을 전달**하는 구조입니다. 앱이 광센서를 이용하여 직접 광량을 측정하지 않습니다.
- MCON K의 NUGU **'아리아' 호출 기능은 포함하지 않습니다**. 순정 내비 음성 호출 지원도 확인되지 않았습니다.
- 카메라 단속 데이터가 순정 내비에서 HUD로 중계되는지는 **미확인**입니다. 화면에 기능이 있다고 거짓으로 표시하지 않았습니다.

## GitHub Actions에서 빌드

1. 이 ZIP을 **압축 해제**합니다. 숨김폴더 `.github`까지 모두 새 GitHub 저장소의 최상위에 업로드합니다.
2. **Actions → Build Dolphin OEM HUD Bridge v0.9 → Run workflow**를 선택합니다. push에도 자동 빌드가 설정되어 있습니다.
3. 성공 시 `DolphinOemHudBridge-v0.9-DASHBOARD-debug` 아티팩트에서 `app-debug.apk`를 받습니다.

앱은 Android 10 이상, minSdk 29 / targetSdk 29, 패키지 `com.dolphin.oemhud`입니다. 빌드는 Java 17, Gradle 8.6, Android Gradle Plugin 8.4.1, Kotlin 플러그인 2.0.21, compileSdk 34, `dev.mobile:dadb:1.2.8`을 사용합니다.

워크플로는 **사전 검사 → TBT/패킷 테스트 → 모의 서비스/실제 전송 코드 테스트 → 대시보드 Java 타입 검사 → ADB Kotlin 오프라인 검사 → 실제 `:app:assembleDebug` → APK 업로드** 순서입니다.

## 검증 범위 및 주의

- 제공된 환경에서는 Android SDK/Gradle와 실물 BYD 차량/HUD에 접근할 수 없어 **실제 `assembleDebug`와 실차 TBT 수신·HUD 화면 출력은 검증하지 못했습니다**. CI에 등록한 빌드 절차가 성공할지는 GitHub에서 실행된 결과가 있어야 확정됩니다.
- 이 패키지의 로컬 Java 타입 검사는 Android SDK 자체가 아니라 **테스트 대체 API 스텁** 기반입니다. Android SDK 정식 컴파일 성공을 의미하지 않습니다.
- 차량 ADB 로컬 포트 `127.0.0.1:5555`가 닫혀 있으면 앱은 포트를 임의로 열지 못하며 시스템/서명 전용 권한을 자동 획득하지 못합니다.
- 공개 AutoNavi 방송 아이콘 정보가 돌핀 순정 내비에도 동일하게 적용되는지 **실차 미확인**입니다. 확신할 수 없는 방향은 임의로 표시하지 않습니다.
- HUD 패킷 전송 성공은 실제 HUD 화면 표시·부저 볼륨·자동 밝기 적용 성공을 증명하지 않습니다.
- 이미 작동하는 MCON K는 **새 앱을 검증하기 전에 삭제하지 않는 것이 안전합니다**. 동시에 두 앱에서 HUD에 BLE 연결하는 것은 피하세요.

기본 UI는 한글로 구성하며 고급 메뉴는 접어두었습니다. 반복적인 실차 테스트를 요구하지 않는 방향으로 로컬 소스 검사를 확장했습니다.

## 개발/오프라인 검사

```bash
python3 tools/preflight.py
bash tools/run_all_simulations.sh
bash tools/run_dashboard_typecheck.sh
# Android SDK/Gradle이 준비된 환경에서
# gradle --no-daemon --stacktrace :app:assembleDebug
```

`TEST_REPORT.md`에 실제 실행 결과와 미검증 사항을 별도로 기록했습니다.
