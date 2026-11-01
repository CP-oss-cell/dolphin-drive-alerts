# GitHub Actions v0.9 실패 분석과 v0.9.1 수정 (2026-11-02)

## 사용자 제출 실제 빌드 로그
- 리포지토리: `CP-oss-cell/dolphin-drive-alerts`, 빌드 커밋 `cc643bfd5a5122053d66e974a9283d6cb855b3ae`.
- `:app:compileDebugKotlin` 성공, `:app:compileDebugJavaWithJavac` 성공.
- 실패: `:app:mergeDebugJavaResource`.
- 원인: `com.android.builder.merge.DuplicateRelativeFileException: 9 files found with path 'META-INF/LICENSE.md'`.
- 충돌 입력은 JUnit Platform/Jupiter의 런타임 의존성 9개.
- 빌드 종료: `BUILD FAILED in 1m 10s`.
- post-job 캐시 `ReserveCacheError`, setup-java deprecation, Node.js 20 경고는 본 컴파일 실패 원인이 아님.

## 적용 수정
- `app/build.gradle`: Android Gradle Plugin 8.x의 `android.packaging.resources.pickFirsts`에 `META-INF/LICENSE.md`, `META-INF/LICENSE-notice.md`, `META-INF/NOTICE.md`만 지정.
- `META-INF/services/**`는 그대로 유지하여 서비스 로딩을 방해하지 않음.
- 버전: `versionCode 10 → 11`, `versionName 0.9-dashboard → 0.9.1-ci-packaging-fix`.
- Actions `build-oem-hud-bridge.yml`: 사전 검사 + APK 확인 + artifact 업로드 유지, 패키징 검사 단계 추가.
- 기존 HUD/TBT/ADB/설정 UI 코드 변경 없음.

## 검증 범위
- `tools/check_packaging_ci_fix.py`: 특정 리소스 중복 방지 설정·APK 산출물 경로 확인.
- `tools/preflight.py`, 기존 JVM/BLE/통합 시뮬레이션, 대시보드 Java 타입 검증 수행.
- GitHub 원격 실제 `assembleDebug` 재실행은 이 세션에서 아직 하지 못함. 해당 변경 후 새 CI 실행으로 최종 판정 필요.

## 기타
- 새 CI에 오류가 생기면 전체 Build debug APK 작업 로그를 기준으로 재점검.
- 주행 안전 안내 기능은 실제 HUD 동작이 확인되기 전 검증 완료로 간주하지 않음.
