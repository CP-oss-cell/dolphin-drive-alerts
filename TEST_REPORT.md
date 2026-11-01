# Dolphin OEM HUD Bridge v0.9 — 소스 검사 결과

## 적용 변경

- **MainActivity.java 완전 대시보드형 개편:** HUD/순정 내비/ADB 상태 요약과 4개 핵심 카드, 접힌 고급 설정/진단으로 통합.
- **메뉴 중복 정리:** 수신 시작/중지 단일 토글, ADB 연결→필수 권한 적용 단일 흐름, 부저+밝기 단일 저장 버튼.
- **기존 기능 보존:** `BridgeService`, `HudConnector`, `HudSettingsProtocol`, `MconHudQProtocol`, `NavCue`, ADB 보안 로직의 기존 프로토콜 변경 없음.
- 버전 `versionCode 10`, `versionName 0.9-dashboard`; GitHub Actions 제목/아티팩트 v0.9로 갱신.
- GitHub Actions에 **화면 Java 컴파일 타입 검사** 단계를 추가. Android 스텁은 검사 목적으로만 생성, 앱에 포함되지 않음.

## 실제 실행한 검사

1. `bash tools/run_all_simulations.sh`: PASS
2. `bash tools/run_dashboard_typecheck.sh`: PASS (Android API 대체 스텁 기반 `javac`)
3. `python3 tools/preflight.py`: PASS (v0.9 화면 계약과 Manifest/코드 보존 검사항목 포함)
4. `.github/workflows/build-oem-hud-bridge.yml`: Python YAML 파싱 성공
5. ZIP CRC/엔트리 및 SHA-256: 패키징 이후 확인

자세한 개별 모의 실행은 `verification/V09_TEST_RESULTS.log`에서 확인하세요. 기존 테스트군은 TBT 파서 45개, BLE 모의 43개, 서비스+BLE 결합 7개, HUD 설정 명령 12개를 포함합니다. 일부 검사 항목은 도구 간 재사용되므로 단순 총합은 독립 검증 수가 아닙니다.

## 미검증 / 실제 빌드의 경계

- ❓ 실제 Android SDK `assembleDebug` 빌드: **실행 불가 (이 환경에 SDK/Gradle 부재)**
- ❓ GitHub Actions 원격 워크플로 실행: **쓰기 권한 거부로 미실행**
- ❓ BYD 순정 내비의 실제 TBT 외부 브로드캐스트 수신
- ❓ 차량 로컬 ADB 승인/권한 획득 성공
- ❓ 아리아 HUD Q 실제 방향/거리/경보음/밝기 출력
- ❌ 카메라 단속정보 HUD 중계는 구현/확인된 기능이 아니며, MCON K의 NUGU '아리아' 음성 인식 기능도 포함하지 않습니다.

검사 통과가 설치 후 기능 보증을 의미하지 않습니다. 차량 내비와 HUD를 실제로 확인하지 않은 것을 완성·검증했다고 표현하지 않습니다.
