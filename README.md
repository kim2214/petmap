# 🐾 PetMap

지도를 이용해 **반려동물과 함께 이용 가능한 카페·식당·시설**을 찾아주는 Android 앱.
공공데이터(한국문화정보원 문화시설·액티비티, 식약처 반려동물 동반출입 음식점, 지방행정 인허가 동물병원)를 정제한 약 25,200개 장소를 지도와 목록으로 보여줍니다.

> 현재 상태: **스토어 배포 운영 중** (versionName 1.9.1 / versionCode 11) — AdMob 광고·UMP 동의·Crashlytics·인앱 리뷰 연동.

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🗺️ 지도 | 네이버 지도에 마커 표시 + 그리드 클러스터링(저줌은 SQL 집계), 카테고리 필터, "이 지역에서 다시 검색", 마커 미리보기 바텀시트 |
| 🔍 검색 | FTS4 유니그램 색인으로 한국어 부분검색, 최근 검색어, 자동완성 |
| 📋 목록 | 검색 + 카테고리 필터 + 거리순 정렬 + 영업중 필터, 즐겨찾기 토글 |
| 🗂️ 지역 | 목록·지도 "지역" 칩 → 시/도 → 시/군/구 2단계 선택(개수 표시). 목록은 그 지역만 표시, 지도는 카메라를 지역 범위로 이동(양 끝 2% 좌표 제외) |
| 📨 제보 | 설정 "새 장소 제보"와 검색 0건 화면의 "이 장소 제보하기"로 메일 템플릿 프리필 |
| 🐕 내 반려견 | 설정에 체중을 등록하면 목록·지도에서 "내 아이(5kg) 가능" 칩으로 체중 제한("10kg 이하", "소형견" 등)에 맞는 곳만 표시 |
| 🚗 빠른 조건 | 체중 등록 없이 "대형견 가능"(중형 상한 25kg 초과 허용) · "주차 가능" 칩 — 목록·지도 공용, SQL 에서 AND |
| ❤️ 즐겨찾기 | 관심 장소를 로컬(Room)에 저장, 삭제 실행취소. "최근 본" 전환으로 최근 연 장소 30곳(SharedPreferences, ID만 저장) |
| 📄 상세 | 주소·전화·운영시간·휴무일, 반려동물 동반 정책(크기/실내외/제한사항), 미니맵, 전화·길찾기·리뷰(네이버 지도/카카오맵 장소 검색)·공유·홈페이지 액션 |
| ⚙️ 기타 | 온보딩, 설정(라이트/다크 테마), 오픈소스 라이선스, 배너 광고(UMP 동의), 인앱 리뷰 |

## 🛠️ 기술 스택

- **빌드 툴체인**: AGP 9.x · Gradle 9.6.1 · Kotlin 2.3 · KSP 2.3 · Hilt 2.60 · Room 2.8 (정확한 버전은 `gradle/libs.versions.toml`)
- **언어/UI**: Kotlin, Jetpack Compose, Material3
- **아키텍처**: 단일 Activity + Compose Navigation, MVVM + Repository, 계층 분리(`data` / `domain` / `ui`)
- **DI**: Hilt
- **로컬 DB**: Room — 프리빌트 에셋 DB(장소) + 즐겨찾기. 장소 데이터는 GitHub Pages 원격 갱신(WorkManager)으로도 교체
- **지도**: 네이버 지도 SDK (`naver-map-compose`)
- **위치**: Google Play Services FusedLocationProvider
- **수익화/품질**: AdMob 배너 + UMP 동의, Firebase Crashlytics/Analytics, Play 인앱 리뷰

## 📁 프로젝트 구조

```
com.kimdev.petmap
├── PetMapApplication.kt       # @HiltAndroidApp 진입점
├── MainActivity.kt            # 단일 Activity (스플래시·테마·온보딩 분기·하단탭 + NavHost)
│
├── core/                      # 공통/인프라 (ads / common / location / review / util)
├── domain/                    # 순수 비즈니스 (모델 + Repository 인터페이스 + 거리·운영시간 유틸)
├── data/                      # 데이터 구현 (local Room / mapper / repository)
├── di/                        # Hilt 모듈 (Database / Dispatcher / Repository)
└── ui/                        # 화면 + ViewModel (map / list / favorite / detail / settings / onboarding / components)
```

> 더 자세한 계층 설명과 데이터 흐름은 [`STRUCTURE.md`](./STRUCTURE.md) 참고.

## 🚀 시작하기

### 요구 사항
- Android Studio (최신 버전 권장) — 빌드 툴체인 **AGP 9.x / Gradle 9.6.1**
- JDK 17+ (Gradle 실행용, 바이트코드는 Java 11 타깃)
- minSdk 24 / targetSdk 36 / compileSdk 37

### 1. 클론
```bash
git clone git@github.com:kim2214/petmap.git
cd petmap
```

### 2. 키 설정 (`local.properties`)
`local.properties`는 git에 포함되지 않으므로 직접 추가해야 합니다.

```properties
# 네이버 클라우드 플랫폼 > Maps > 발급받은 Client ID (NCP Key ID)
naver.map.clientId=YOUR_NAVER_MAP_CLIENT_ID
```

- **장소 데이터는 앱에 내장**되어 있어 키가 없어도 목록/지도 마커가 표시됩니다. 단, **지도 타일 렌더링에는 네이버 키가 필요**합니다.
- 네이버 SDK 버전에 따라 매니페스트의 메타데이터 키 이름이 다릅니다
  (신규 콘솔: `com.naver.maps.map.NCP_KEY_ID` / 구형 콘솔: `com.naver.maps.map.CLIENT_ID`).

### 3. 빌드 & 실행
```bash
./gradlew :app:assembleDebug      # APK 빌드
# 또는 Android Studio에서 Sync 후 Run ▶
```

### 4. 릴리스 빌드 (R8 + ABI 분리 + 서명)
릴리스는 R8 minify·리소스 축소가 켜져 있고 ABI별로 APK가 분리된다(arm64-v8a ≈ 37MB).

```bash
./gradlew :app:assembleRelease    # ABI별 APK (app/build/outputs/apk/release/) — 로컬 검증 전용
./gradlew :app:bundleRelease      # 스토어용 App Bundle(.aab) — Play가 ABI 분리
```

- `assembleRelease` 산출물은 모든 ABI가 같은 versionCode 라 **스토어 업로드용이 아니다**(로컬 검증·사이드로드용). 배포는 `.aab` 로 한다.
- 서명은 `keystore.properties`(git 제외)에서 읽는다. 없으면 `assembleRelease`는 경고와 함께 디버그 서명으로 폴백하고, `bundleRelease`는 **실패**한다(로컬 검증은 `-PallowDebugSigning`).
스토어 배포용 키를 만들려면:
```bash
keytool -genkeypair -v -keystore release.keystore -alias petmap \
  -keyalg RSA -keysize 2048 -validity 10000
```
그리고 프로젝트 루트에 `keystore.properties` 작성(절대 커밋 금지, 키스토어 파일 백업 필수):
```properties
storeFile=release.keystore
storePassword=...
keyAlias=petmap
keyPassword=...
```

### 5. 크래시 리포트 / 분석 (Firebase)
Crashlytics·Analytics 가 연동돼 있으나, **본인 Firebase 프로젝트의 `google-services.json` 이 있어야 활성화**된다.
파일이 없으면 빌드는 정상이고 Firebase 만 비활성(앱 시작 시 경고 로그만 출력)된다.

활성화 방법:
1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트 생성 → Android 앱 추가
   (패키지 이름: `com.kimdev.petmap`)
2. `google-services.json` 다운로드 → `app/google-services.json` 에 배치 (git 제외됨)
3. 다시 빌드하면 자동으로 플러그인이 적용되고 크래시·기본 분석이 수집된다.

> 크래시는 자동 수집(앱 코드에서 Firebase API 직접 호출 없음)이라, json 유무와 무관하게 앱은 안전하게 동작한다.
> 비공개 테스트 중 크래시는 Firebase 콘솔 Crashlytics 에서 확인할 수 있다.

## 🗃️ 데이터 전략 (프리빌트 Room DB)

API 통신 부담을 최소화하기 위해 **미리 만든 Room DB를 단일 소스로** 사용한다.

- `app/src/main/assets/petmap.db` — 한국문화정보원 "전국 반려동물 동반 가능 문화시설" + "문화 여가 활동 시설(액티비티)" 데이터셋(+ 선택: 식약처 "반려동물 동반출입 음식점" 지오코딩 결과, 지방행정 인허가 "동물병원" 영업 중 목록)을 정제해 넣은 SQLite DB(v10 — 주차·이용료·애견 추가요금·장소설명, 시/도 명칭 현행화, 법인 표기("(주)" 등) 제거·`sortKey` 이름순 정렬 키, `meta` 데이터 기준일 포함, 실내/실외 여부는 **nullable = 정보 없음**, `maxPetKg` 는 "입장 가능 동물 크기" 문구를 숫자화한 최대 체중). `이름+좌표`(및 같은 이름 150m 이내) 기준 중복 제거 후 **동반 가능(Y) 장소만 약 25,200개**를 수록한다(동반 불가 N 은 제외). 인허가 데이터에 폐업으로 기록된 동물병원·동물약국(약 1,400곳)은 뺀다. 식약처 음식점 약 2,640곳(식당 1,836·카페/제과 약 800)은 2026-03 시행 반려동물 동반출입 음식점 등록 업소로, 좌표는 네이버 Geocoding 결과이며 크기·실내외 조건은 정보 없음(null)으로 들어간다. 동물병원은 한국문화정보원 데이터에 인허가 데이터에만 있는 약 1,870곳을 더하고 폐업 약 410곳을 빼 약 5,900곳이다 — 좌표는 원본 EPSG:5174 를 `tools/fetch_hospitals.py` 가 WGS84 로 변환(기존 데이터 대비 중앙값 오차 약 5m), 같은 건물(30m) 안의 표기만 다른 병원("24시"·지점명·괄호 차이)은 중복으로 본다.
- 첫 실행 시 Room `createFromAsset` 로 에셋 DB를 즉시 로드(파싱/시딩/색인 구축 없음).
- 지도/목록/검색은 모두 **로컬 Room 쿼리**다. 네트워크는 장소 데이터 원격 갱신에만 쓴다(아래).
- **원격 데이터 갱신 (앱 업데이트 없이)**: `tools/publish_db.py` 가 에셋 DB 를 gzip 해
  `kim2214.github.io/petmap/data/<identity_hash>/`(`manifest.json` + `petmap-<기준일>.db.gz`)에 게시한다.
  앱은 하루 한 번(Wi-Fi·배터리 여유, `PlaceDataUpdateWorker`) 또는 설정 "새 데이터 확인"에서 매니페스트를 읽고,
  기준일이 자기 `meta.data_date` 보다 늦으면 내려받아 SHA-256·identity_hash·장소 수·FTS 행 수를 검증한 뒤
  **한 트랜잭션으로** places·`places_fts`·meta 를 교체한다(`PlaceDataUpdaterImpl`). 실패하면 기존 데이터가 남고,
  즐겨찾기는 별도 테이블이라 영향이 없다. 스키마별 경로라 엔티티가 바뀐 데이터는 구버전 앱에 가지 않는다.
- **기존 설치의 데이터 갱신은 DB 버전 업으로 전달**된다: 마이그레이션 대신 Room 이 구버전 파일을 지우고 에셋을
  재복사한다(`PetMapDatabase.VERSION`/`RECREATED_FROM`). 즐겨찾기는 `files/favorites_backup.json` 미러가
  재생성 직전에 보존하고 첫 접근 시 복원한다. 데이터 갱신(폐업 정리 등)으로 빠진 즐겨찾기는 목록에 남고, 상세는 저장된 스냅샷과 "최신 데이터에 없는 장소" 안내로 열린다. 설정 화면의 "데이터 갱신일"은 에셋 `meta.data_date` 다.
- **검색은 FTS4 색인** 사용. 색인(`places_fts`)은 `build_db.py` 가 에셋에 **동봉**한다. 한국어 부분검색을 보존하기 위해 글자 단위 유니그램으로
  색인하고 검색어를 구문(phrase)으로 변환한다 — 자세한 건 `data/local/PlaceFts.kt` 참고.
- 에셋 DB 로드·FTS 검색·meta 계약은 androidTest `PetMapDatabaseAssetTest`, 버전 업 재생성·즐겨찾기 보존은 `LegacyDatabaseRecreateTest`, 원격 갱신(교체·검증 실패 시 유지)은 `PlaceDataUpdaterImplTest` 가 실기기에서 검증한다.

### ⚡ Baseline Profile

콜드 스타트 JIT 비용을 줄이기 위해 릴리스에 Baseline Profile 을 동봉한다(`androidx.profileinstaller`).

- 프로필: `app/src/release/generated/baselineProfiles/{baseline-prof,startup-prof}.txt` — 리포에 커밋되며 빌드 시 자동 생성하지 않는다.
- 재생성(시작 경로나 주요 화면 구조를 크게 바꿨을 때): API 33+ 에뮬레이터/기기를 연결하고
  ```bash
  ./gradlew :app:generateBaselineProfile
  ```
  `:baselineprofile/…/BaselineProfileGenerator.kt` 가 온보딩 건너뛰기 → 지도 → 목록 스크롤 → 즐겨찾기 → 설정을 재현한다
  (하단 탭 라벨 텍스트로 탐색하므로 라벨을 바꾸면 함께 수정). 생성용 `nonMinifiedRelease` 는 AdMob 테스트 ID 를 쓴다.

### 📦 데이터 갱신 절차
데이터셋이 갱신되면 아래로 프리빌트 DB를 다시 만든다.

1. [공공데이터포털](https://www.data.go.kr)에서 "전국 반려동물 동반 가능 문화시설"(15111389)과
   "전국 문화 여가 활동 시설(액티비티)"(15111393) CSV를 새로 받는다.
2. (선택) 식약처 반려동물 동반출입 음식점 목록을 지오코딩해 CSV 로 만든다 — 네이버 Geocoding(Client Secret) 또는
   카카오 REST 키가 `local.properties`/환경변수에 있어야 한다:
   ```bash
   python3 tools/fetch_restaurants.py            # → tools/data/restaurants.csv (좌표 캐시 재사용)
   python3 tools/fetch_hospitals.py              # → tools/data/hospitals.csv (지방행정 인허가 동물병원, 키 불필요)
   python3 tools/fetch_closures.py               # → tools/data/closed.csv (인허가 폐업 동물병원·동물약국)
   ```
3. 빌드 스크립트 실행 → `assets/petmap.db` 재생성 (여러 CSV 를 순서대로, 먼저 준 파일이 중복 시 우선):
   ```bash
   python3 tools/build_db.py --data-date=YYYY-MM-DD [--closed=tools/data/closed.csv] "<반려동물 CSV>" "<액티비티 CSV>" [tools/data/restaurants.csv] [tools/data/hospitals.csv]
   # 출력: app/src/main/assets/petmap.db (고유 장소 N건)
   ```
4. 기존 설치에 전달하는 방법은 둘 중 하나:
   - **원격 게시(권장, 데이터만 바뀐 경우)** — 앱 업데이트 없이 하루 안에 반영:
     ```bash
     python3 tools/publish_db.py ../kim2214.github.io   # → petmap/data/<identity_hash>/ 생성, 이후 그 리포에서 커밋·푸시
     ```
     바뀐 `assets/petmap.db` 도 커밋해 두면 새 설치는 처음부터 새 데이터로 시작한다(DB 버전은 그대로 둔다).
   - **앱 업데이트** — `PetMapDatabase.VERSION` 을 올리고(`/rebuild-db` 참고) 배포. 스키마가 바뀌었으면 이 경로만 가능.

> 스크립트는 앱의 Room 스키마(테이블·인덱스·`room_master_table` identity_hash·`user_version`)와 FTS 색인을 그대로 재현하므로 `createFromAsset` 가 그대로 로드한다.
> **주의**: 엔티티(`PlaceEntity`/`FavoriteEntity`) 스키마를 바꾸면 identity_hash 가 달라진다. 기준값은 빌드가 갱신하는 `app/schemas/**/<version>.json` 의 `identityHash` 이며, `tools/build_db.py` 의 `ROOM_IDENTITY_HASH`(및 `DB_VERSION`)를 함께 반영해야 한다. 자세한 절차는 `/rebuild-db` 스킬 참고.

## 🗺️ 로드맵

- [x] 프리빌트 Room DB(createFromAsset) 단일 소스 — 첫 실행 즉시 로드, FTS 색인 동봉
- [x] 지도 뷰포트(반경) 기반 로컬 조회 + 저줌 SQL 그리드 집계
- [x] 위치 권한 요청 + 내 위치로 카메라 이동(네이버 위치 오버레이 + Follow)
- [x] 마커 클러스터링 (도심 밀집 대응, 겹친 곳 목록 펼치기)
- [x] 마커 클릭 시 바텀시트 미리보기
- [x] 거리순 정렬, 영업중 필터(운영시간 파싱)
- [x] 상세 화면 액션: 전화 · 길찾기(네이버 지도) · 공유 · 홈페이지
- [x] 온보딩 · 설정(테마) · 오픈소스 라이선스 · 배너 광고(UMP) · 인앱 리뷰
- [x] CI (단위 테스트 + Lint + R8 릴리스 빌드) · 에셋 DB 계약 테스트
- [x] 목록 페이징 + 반려동물 동반 조건 필터(모든 크기/실내/실외) — 지도에도 동일 필터(SQL 단계)
- [x] 목록 거리순은 내 주변 30km 안만 (0건이면 거리순 해제 안내)
- [x] 지도 검색 확정(키보드 검색 키) 시 결과 전체(≤200)를 마커로 표시, 카메라 자동 맞춤
- [x] 상세 화면 딥링크 (공유 → 앱 진입, HTTPS App Link `kim2214.github.io/petmap/place?id=…` + 구버전 `petmap://place/{id}`)
- [x] 상세 → "지도에서 보기"는 포커스 지도를 push(`mapFocus?focus=id`, 별도 라우트·하단탭 숨김) — 뒤로가기로 상세 복귀
- [x] 즐겨찾기 백업(미러 파일) · 추가일 정렬 · 메모
- [x] 내 반려견 체중 프로필 + "내 아이 가능" 필터 (DB v7 `maxPetKg`)
- [x] 지역 브라우징(시/도 → 시/군/구, 주소 접두어 LIKE) · 새 장소 제보 진입점 (DB v8 시/도 명칭 정규화)
- [x] "대형견 가능"·"주차 가능" 빠른 칩 (DB 변경 없음 — 기존 `maxPetKg`·`parkingAvailable` 활용)
- [x] 최근 본 장소 (즐겨찾기 탭 세그먼트, 최대 30곳, Room 스키마 변경 없음)
- [x] 상세 "리뷰" → 네이버 지도/카카오맵 장소 검색(앱 딥링크, 미설치 시 모바일 웹)
- [ ] HTTPS App Links (미설치 기기용 링크)

## 📄 라이선스

미정.
