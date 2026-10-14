# PetMap

반려동물 동반 가능 장소를 지도/목록으로 보여주는 네이티브 Android 앱.
패키지 `com.kimdev.petmap` · Kotlin + Jetpack Compose · 단일 Activity + Compose Navigation · MVVM + Repository · Hilt(DI).

자세한 계층 구조와 데이터 흐름은 `STRUCTURE.md`, 기능·설정은 `README.md` 참고.

## 빌드 / 테스트 명령

```bash
./gradlew :app:compileDebugKotlin   # 컴파일만 (빠른 검증)
./gradlew :app:assembleDebug        # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JUnit 단위 테스트
python3 tools/verify_db.py          # 에셋 DB ↔ Room 스키마 동기화 오프라인 검사
```

- 오래 걸리는 Gradle 작업은 `timeout 580 ./gradlew ...` 로 감싼다.
- 실기기/에뮬레이터 실행: `adb install -r <apk>` → `adb shell am start -n com.kimdev.petmap/.MainActivity` → `adb logcat` 로 크래시 확인.
- 빌드 툴체인: **AGP 9.x / Gradle 9.6.1 / Kotlin 2.3 / KSP 2.3 / Hilt 2.60 / Room 2.8**. 정확한 버전은 `gradle/libs.versions.toml`·`gradle/wrapper/gradle-wrapper.properties`가 단일 소스.
- Gradle 실행에는 JDK 17+ 필요하나 바이트코드는 Java 11 타깃(`compilerOptions.jvmTarget=JVM_11`, `sourceCompatibility=11`).
- SDK: minSdk 24 / targetSdk 36 / compileSdk 37. 버전: versionCode 11, versionName 1.9.1.
- **Baseline Profile**: `app/src/release/generated/baselineProfiles/`(커밋됨)를 릴리스에 동봉. 시작 경로·주요 화면을 크게 바꿨으면 에뮬레이터/기기(API 33+) 연결 후 `./gradlew :app:generateBaselineProfile`로 재생성(`:baselineprofile` 모듈, 화면 라벨 텍스트로 탐색). 빌드 중 자동 생성은 꺼져 있다.

## 릴리스

```bash
./gradlew :app:assembleRelease      # ABI별 APK (arm64-v8a 등, R8 minify + 리소스 축소)
./gradlew :app:bundleRelease        # 스토어용 .aab (Play가 ABI 분리)
```

- 서명은 `keystore.properties`(git 제외)에서 읽고, 없으면 디버그 서명으로 폴백(로컬 검증용).
- 릴리스 빌드만 **실제 AdMob ID** 사용(`app/build.gradle.kts` release 블록). 디버그는 Google 테스트 ID.
- **배포 순서 (필수)**: ① versionCode/versionName 범프를 커밋 → ② 그 커밋에 `vX.Y` 태그 → ③ 그 상태에서 `bundleRelease` → 업로드. 커밋 없이 배포하면 스토어 빌드를 재현할 소스가 사라진다(1.5 때 실제 발생, 히스토리 재작성으로 복구).

## 데이터: 프리빌트 Room DB

- 단일 소스는 `app/src/main/assets/petmap.db`(약 26,600개 고유 장소, v10 — 동물병원은 인허가 데이터로 보강, 동반가능 Y 만 수록, 시/도 명칭 현행화(강원특별자치도·전북특별자치도·전남광주통합특별시), 법인 표기 제거 + `sortKey`(한글→영문→숫자 이름순), 주차·요금·설명·`meta` 기준일 포함, 실내/실외는 nullable = 정보 없음, `maxPetKg` 는 크기 문구를 숫자화한 입장 가능 최대 체중). 첫 실행 시 Room `createFromAsset`로 즉시 로드.
- **원격 데이터 갱신 (앱 업데이트 없이)**: `python3 tools/publish_db.py <kim2214.github.io 경로>` 가 에셋 DB 를 `petmap/data/<identity_hash>/`(manifest.json + gz)로 만든다 → 그 리포에서 커밋·푸시. 앱(`PlaceDataUpdaterImpl`, 하루 1회 Wi-Fi `PlaceDataUpdateWorker` + 설정 "새 데이터 확인")은 기준일(`meta.data_date`)이 **더 늦을 때만** 받아 검증 후 places·places_fts·meta 를 한 트랜잭션으로 교체한다 → 고쳐서 다시 게시할 땐 `--data-date` 를 올려야 전달된다. 데이터만 바뀐 갱신은 이 경로로 충분(DB 버전 업 불필요), 스키마가 바뀌면 앱 업데이트만 가능.
- **데이터 갱신 전략(앱 업데이트) = DB 버전 업 → 파괴적 재생성.** 마이그레이션은 쓰지 않는다. `PetMapDatabase.VERSION`을 올리고 직전 버전을 `RECREATED_FROM`에 추가하면 Room이 기존 파일을 지우고 에셋을 재복사한다. 즐겨찾기는 `FavoriteBackup` 미러가 재생성 직전에 보존(`preserveLegacyDatabase`, `DatabaseModule`)하고 `ensureSeeded`가 복원한다. 앱 업데이트로 전달하려면 **장소 데이터만 바뀌어도 버전을 올려야 한다** (원격 게시로 보낼 땐 불필요).
- 검색은 FTS4 유니그램 색인(`data/local/PlaceFts.kt`). 색인은 에셋에 동봉되며(`build_db.py`의 `fts_index`가 `PlaceFts.index`와 동일 로직이어야 함).
- **DB를 새 CSV로 재생성할 때는 `/rebuild-db` 스킬을 사용한다** (`tools/build_db.py`의 identity_hash/버전 주의사항이 얽혀 있음). 원본 CSV 는 리포에 없다 — 공공데이터포털에서 받는다(다운로드 URL 은 스킬 참고). 식약처 "반려동물 동반출입 음식점"(약 2,700곳, 좌표 없음)은 `tools/fetch_restaurants.py`(네이버 Geocoding, `naver.map.clientSecret`)로 CSV 를 만들어 세 번째 입력으로, 지방행정 인허가 "동물병원"은 `tools/fetch_hospitals.py`(키 불필요, EPSG:5174→WGS84 변환)로 만들어 네 번째(마지막) 입력으로 넘긴다. 좌표 캐시 `tools/data/`는 git 제외.
- 엔티티(`PlaceEntity`/`FavoriteEntity`/`MetaEntity`) 스키마를 바꾸면 identity_hash가 달라진다. 기준값은 `app/schemas/**/<version>.json`의 `identityHash`이며 `tools/build_db.py`의 `ROOM_IDENTITY_HASH`를 함께 갱신해야 한다. 동기화가 어긋나면 androidTest `PetMapDatabaseAssetTest`가 실패한다. 재생성·즐겨찾기 보존 계약은 `LegacyDatabaseRecreateTest`.

## 비밀 키 (git 제외)

- `local.properties`: `naver.map.clientId` (AdMob ID 는 비밀이 아니라 `app/build.gradle.kts`에 하드코딩). 선택: `naver.map.clientSecret` 또는 `kakao.restApiKey` — 앱이 아니라 `tools/fetch_restaurants.py` 지오코딩 전용.
- `keystore.properties`, `petmap-upload.jks`: 릴리스 서명.
- `app/google-services.json`: 있으면 Firebase Crashlytics/Analytics 활성화, 없으면 앱은 정상 빌드(Firebase만 비활성).

## 외부 URL (kim2214.github.io)

- 개인정보처리방침: `https://kim2214.github.io/privacy-policy.html` (앱 내 참조: `ui/settings/SettingsScreen.kt`).
- AdMob `app-ads.txt`: `https://kim2214.github.io/app-ads.txt`.
- 원격 장소 데이터: `https://kim2214.github.io/petmap/data/<identity_hash>/manifest.json` (+ gz). 기준 URL 은 `core/common/Constants.DATA_UPDATE_BASE_URL`, 생성은 `tools/publish_db.py`. 로컬 체크아웃: `../kim2214.github.io`.
- 모두 별도 리포 `kim2214/kim2214.github.io`(GitHub Pages)에서 서빙된다.

## 커밋 규칙

- 커밋 메시지는 한국어. **`Co-Authored-By` 트레일러를 넣지 않는다.**
- 작업과 무관한 변경(예: IDE 자동 버전 범프)은 함께 커밋하지 않는다.
