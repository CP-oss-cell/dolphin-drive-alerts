# v0.9.1 변경 사항

- **실제 GitHub 실패 로그**: `:app:mergeDebugJavaResource`에서 JUnit 의존성 9개의 `META-INF/LICENSE.md` 충돌.
- **수정**: `app/build.gradle`의 `packaging.resources.pickFirsts`에 확인된 라이선스 중복 파일 3개를 명시.
- **업데이트 호환**: `versionCode 11`로 상향. 앱 ID·최소 SDK·권한 선언 동일.
- **YML**: 패키징 사전 확인 단계 추가; 실제 빌드와 APK artifact 업로드 유지.
- **UI**: 기존 v0.9 한글 대시보드 그대로 유지, 메뉴 추가·중복 없음.
- **기존 기능**: TBT / HUD BLE / 경보음·밝기 / ADB 변경 없음.
- **검증**: 로컬 회귀 PASS. Android SDK 원격 컴파일 및 실차는 미검증.
