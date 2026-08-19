# 🐾 PetMap

지도를 이용해 **반려동물과 함께 이용 가능한 카페·식당·시설**을 찾아주는 Android 앱.
공공데이터(한국문화정보원)를 정제한 약 22,127개 장소를 지도와 목록으로 보여줍니다.

> 현재 상태: **스토어 배포 운영 중** (versionName 1.4 / versionCode 5) — AdMob 광고·UMP 동의·Crashlytics·인앱 리뷰 연동.

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🗺️ 지도 | 네이버 지도에 마커 표시 + 그리드 클러스터링(저줌은 SQL 집계), 카테고리 필터, "이 지역에서 다시 검색", 마커 미리보기 바텀시트 |
| 🔍 검색 | FTS4 유니그램 색인으로 한국어 부분검색, 최근 검색어, 자동완성 |
| 📋 목록 | 검색 + 카테고리 필터 + 거리순 정렬 + 영업중 필터, 즐겨찾기 토글 |
| ❤️ 즐겨찾기 | 관심 장소를 로컬(Room)에 저장, 삭제 실행취소 |
| 📄 상세 | 주소·전화·운영시간·휴무일, 반려동물 동반 정책(크기/실내외/제한사항), 미니맵, 전화·길찾기·공유·홈페이지 액션 |
| ⚙️ 기타 | 온보딩, 설정(라이트/다크 테마), 오픈소스 라이선스, 배너 광고(UMP 동의), 인앱 리뷰 |

## 🛠️ 기술 스택

- **빌드 툴체인**: AGP 9.x · Gradle 9.3.1 · Kotlin 2.3 · KSP 2.3 · Hilt 2.57 · Room 2.8 (정확한 버전은 `gradle/libs.versions.toml`)
- **언어/UI**: Kotlin, Jetpack Compose, Material3
- **아키텍처**: 단일 Activity + Compose Navigation, MVVM + Repository, 계층 분리(`data` / `domain` / `ui`)
- **DI**: Hilt
- **로컬 DB**: Room — 프리빌트 에셋 DB(장소) + 즐겨찾기. 네트워크 경로 없음
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
- Android Studio (최신 버전 권장) — 빌드 툴체인 **AGP 9.x / Gradle 9.3.1**
- JDK 17+ (Gradle 실행용, 바이트코드는 Java 11 타깃)
- minSdk 24 / targetSdk 36 / compileSdk 36

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

- `app/src/main/assets/petmap.db` — 한국문화정보원 "전국 반려동물 동반 가능 문화시설" + "문화 여가 활동 시설(액티비티)" 데이터셋을 정제해 넣은 SQLite DB(v4 — 주차·이용료·애견 추가요금·장소설명 포함). `이름+좌표` 기준 중복 제거 후 **동반 가능(Y) 장소만 약 22,127개**를 수록한다(동반 불가 N 은 제외).
- 첫 실행 시 Room `createFromAsset` 로 에셋 DB를 즉시 로드(파싱/시딩/색인 구축 없음).
- 지도/목록/검색은 모두 **로컬 Room 쿼리** — 네트워크 경로 자체가 없다. 데이터 갱신은 아래 재생성 절차로만 이뤄진다.
- **검색은 FTS4 색인** 사용. 색인(`places_fts`)은 `build_db.py` 가 에셋에 **동봉**하며, 구버전 설치(v2 DB)만
  `Migration(2→3)` 이 업그레이드 시 구축한다. 한국어 부분검색을 보존하기 위해 글자 단위 유니그램으로
  색인하고 검색어를 구문(phrase)으로 변환한다 — 자세한 건 `data/local/PlaceFts.kt` 참고.
- 에셋 DB 로드·FTS 검색 계약은 androidTest `PetMapDatabaseAssetTest` 가 실기기에서 검증한다.

### 📦 데이터 갱신 절차
데이터셋이 갱신되면 아래로 프리빌트 DB를 다시 만든다.

1. [공공데이터포털](https://www.data.go.kr)에서 "전국 반려동물 동반 가능 문화시설"과
   "전국 문화 여가 활동 시설(액티비티)" CSV를 새로 받는다.
2. 빌드 스크립트 실행 → `assets/petmap.db` 재생성 (여러 CSV 를 순서대로, 먼저 준 파일이 중복 시 우선):
   ```bash
   python3 tools/build_db.py "<반려동물 CSV>" "<액티비티 CSV>"
   # 출력: app/src/main/assets/petmap.db (고유 장소 N건)
   ```
3. 앱을 다시 빌드/설치하면 새 데이터가 반영된다.

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
- [x] 목록 페이징 + 반려동물 동반 조건 필터(모든 크기/실내/실외)
- [x] 상세 화면 딥링크 (공유 → 앱 진입, petmap://place/{id})
- [x] 즐겨찾기 백업(미러 파일) · 추가일 정렬 · 메모
- [ ] HTTPS App Links (미설치 기기용 링크)

## 📄 라이선스

미정.
