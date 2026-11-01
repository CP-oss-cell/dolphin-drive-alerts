# GitHub Actions v0.17.37

1. 저장소 루트에 `DolphinDriveAlerts-v0.17.37-INTERNAL-SPLIT-source.zip` 업로드.
2. `.github/workflows/build-v01737-internal-split.yml` 업로드 (또는 빌드팩의 동일 경로 사용).
3. GitHub Actions에서 `Build Dolphin Drive Alerts v0.17.37 Internal Split Engine`의 Run workflow 실행.
4. `DOLPHIN_DEBUG_KEYSTORE_B64` Secret에 기존 APK와 같은 서명 키를 넣어야 **삭제 없는 업데이트 설치** 가능. 새 임시 키로 빌드하면 기존 APK 위에 설치 불가.
5. `assembleDebug`, 서명 확인, APK 내보내기는 GitHub에서 실행. 이번 로컬 환경에서는 APK를 만들지 못했음.
6. 실행 결과에서 비율/카메라 복원이 실제 적용되는지는 별도 실차 증거 필요.
