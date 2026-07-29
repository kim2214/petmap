# PetMap

반려동물 동반 가능 장소를 지도/목록으로 보여주는 네이티브 Android 앱.
패키지 `com.kimdev.petmap` · Kotlin + Jetpack Compose · 단일 Activity + Compose Navigation · MVVM + Repository · Hilt(DI).

자세한 계층 구조와 데이터 흐름은 `STRUCTURE.md`, 기능·설정은 `README.md` 참고.

## 빌드 / 테스트 명령

```bash
./gradlew :app:compileDebugKotlin   # 컴파일만 (빠른 검증)
./gradlew :app:assembleDebug        # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JUnit 단위 테스트
```

- 오래 걸리는 Gradle 작업은 `timeout 580 ./gradlew ...` 로 감싼다.
- 실기기/에뮬레이터 실행: `adb install -r <apk>` → `adb shell am start -n com.kimdev.petmap/.MainActivity` → `adb logcat` 로 크래시 확인.
- 빌드 툴체인: **AGP 9.x / Gradle 9.3.1 / Kotlin 2.3 / KSP 2.3 / Hilt 2.60 / Room 2.8**. 정확한 버전은 `gradle/libs.versions.toml`·`gradle/wrapper/gradle-wrapper.properties`가 단일 소스.
- Gradle 실행에는 JDK 17+ 필요하나 바이트코드는 Java 11 타깃(`compilerOptions.jvmTarget=JVM_11`, `sourceCompatibility=11`).
- SDK: minSdk 24 / targetSdk 36 / compileSdk 37. 버전: versionCode 6, versionName 1.5.

## 릴리스

```bash
./gradlew :app:assembleRelease      # ABI별 APK (arm64-v8a 등, R8 minify + 리소스 축소)
./gradlew :app:bundleRelease        # 스토어용 .aab (Play가 ABI 분리)
```

- 서명은 `keystore.properties`(git 제외)에서 읽고, 없으면 디버그 서명으로 폴백(로컬 검증용).
- 릴리스 빌드만 **실제 AdMob ID** 사용(`app/build.gradle.kts` release 블록). 디버그는 Google 테스트 ID.
- **배포 순서 (필수)**: ① versionCode/versionName 범프를 커밋 → ② 그 커밋에 `vX.Y` 태그 → ③ 그 상태에서 `bundleRelease` → 업로드. 커밋 없이 배포하면 스토어 빌드를 재현할 소스가 사라진다(1.5 때 실제 발생, 히스토리 재작성으로 복구).

## 데이터: 프리빌트 Room DB

- 단일 소스는 `app/src/main/assets/petmap.db`(약 23,925개 고유 장소, v3). 첫 실행 시 Room `createFromAsset`로 즉시 로드 — 네트워크 경로 없음(원격 동기화 코드는 제거됨).
- 검색은 FTS4 유니그램 색인(`data/local/PlaceFts.kt`). 색인은 에셋에 동봉되며(`build_db.py`의 `fts_index`가 `PlaceFts.index`와 동일 로직이어야 함), `Migration(2→3)`은 구버전 설치(v2 DB) 업그레이드용.
- **DB를 새 CSV로 재생성할 때는 `/rebuild-db` 스킬을 사용한다** (`tools/build_db.py`의 identity_hash/버전 주의사항이 얽혀 있음).
- 엔티티(`PlaceEntity`/`FavoriteEntity`) 스키마를 바꾸면 identity_hash가 달라진다. 기준값은 `app/schemas/**/<version>.json`의 `identityHash`이며 `tools/build_db.py`의 `ROOM_IDENTITY_HASH`를 함께 갱신해야 한다. 동기화가 어긋나면 androidTest `PetMapDatabaseAssetTest`가 실패한다.

## 비밀 키 (git 제외)

- `local.properties`: `naver.map.clientId` (AdMob ID 는 비밀이 아니라 `app/build.gradle.kts`에 하드코딩).
- `keystore.properties`, `petmap-upload.jks`: 릴리스 서명.
- `app/google-services.json`: 있으면 Firebase Crashlytics/Analytics 활성화, 없으면 앱은 정상 빌드(Firebase만 비활성).

## 외부 URL (kim2214.github.io)

- 개인정보처리방침: `https://kim2214.github.io/privacy-policy.html` (앱 내 참조: `ui/settings/SettingsScreen.kt`).
- AdMob `app-ads.txt`: `https://kim2214.github.io/app-ads.txt`.
- 두 파일은 별도 리포 `kim2214/kim2214.github.io`(GitHub Pages)에서 서빙된다.

## 커밋 규칙

- 커밋 메시지는 한국어. **`Co-Authored-By` 트레일러를 넣지 않는다.**
- 작업과 무관한 변경(예: IDE 자동 버전 범프)은 함께 커밋하지 않는다.
