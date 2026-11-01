# Dolphin Drive Alerts v0.17.37 — 내부 분할 통합 수정 (2026-11-02)

## 사용자 요청 수정
- AutoSplit 설치/실행/딥링크 위임이 아니라 *Dolphin Drive 내부*에서 동등한 분할 기능을 제공한다는 의미로 수정.
- v0.17.36 외부 앱 호출은 오류였으며 폐기. `AutoSplitBridge.kt` 완전 삭제.
- v0.17.35의 독립 작동 코드를 원상 복구하고, 같은 6개 카드 대시보드 유지.

## 실제 내부 기능 연결
1. 내장 분할 프로필 이름 저장 / 두 앱 선택 / 비율 10%~90% / 삭제·편집 / 런처 바로가기 요청.
2. 로컬 ADB 승인 후 선택 앱 두 개를 Android 10 `WINDOWING_MODE_SPLIT_SCREEN_PRIMARY=3` 및 `SECONDARY=4` Task로 배치. 필요하면 임시 Ghost Activity와 Task move 사용.
3. `am stack list` + `dumpsys activity activities` 읽기 및 Task ID·mode 검증.
4. `am stack resize-docked-stack` 두 사각형(8개 좌표) 기반 비율 변경. 명령 후 실제 창 경계 %를 재조회하며 미적용이면 실패/부분 성공으로 분류.
5. 전체화면·360 카메라 사용 시 분할 복원 감시 활성화. 카메라가 최상단인 동안 분할/비율 변경 금지.
6. 시동 시 자동 실행에서 내부 `split:<profileId>` 프로필 재연결, 기존 프로필 ID 및 사용자 설정 보존.
7. 이전에 무관한 두 stack의 경계만으로 분할 상태를 추정하던 fallback 제거: 명시적 split-primary/secondary 필요.
8. AutoSplit 설치가 전혀 필요하지 않음. 외부 앱의 서명·보호 바이트코드를 복사하지 않고 Android 동작 방식 분석을 이용해 원래 Kotlin 코드 유지/보강.

## 참고 APK에서 코드 실행 흐름 정적 확인
- BydAutoSplit 1.2.0 (사용자 제공): `SplitScreenController.launchSync`의 `am start --windowingMode 3/4`, `am stack move-task` 문자열.
- `SplitRatioController.apply/verify`: `am stack list`, `am stack resize-docked-stack`, `split-screen-primary`/`split-screen-secondary` 확인, 변경 후 비율 검증.
- `CameraRestoreService`: 카메라 진입 시 기존 split 여부 기록, 카메라 종료 후 복원 및 ratio 재확인/재시도.
- 별도 APK 의존성을 넣지 않고 공개 Android 10 ActivityManager 명령 경로를 사용.

## 로컬 검증
- `tools/verify_v01737_internal_split.py`의 24개 연결 검증과 Kotlin 비율·명령 형식 실행 검사 통과.
- `verify_v01735_feature_integration.py` 19 SOURCE ONLY PASS; `verify_v01734_dashboard.py` 23 PASS; `verify_v01733_self_app_dpi.py` 12 PASS; `verify_v01729_unified_evidence.py` 20 PASS.
- GitHub Actions YAML 파싱과 bash 구문 검사 17개 PASS; ZIP CRC PASS.
- Android Gradle 컴파일/assembleDebug와 실차 비율·복귀 최종 검증은 **미실행**. SDK/Gradle 환경 없이 성공을 확약하지 않음.
- Android/ADB/OEM 펌웨어 제약으로 `am stack resize-docked-stack`이 거부될 수 있음; 코드가 그대로 작동한다고 보장하지 않음.

## 전 버전과 충돌 없음
- 6개 대시보드 카드 구조 유지. 외부 AutoSplit 메뉴 추가 없음.
- 23종 음성 알림/노멀 모드 진단/앱 자체 DPI/내비 오디오 진단/로그 전부 그대로 보존.
- applicationId `com.byd.dolphin.drivealerts`, versionCode 63. 기존 키로 빌드해야 앱 삭제 없이 업데이트 가능.
