# PetMap 프로젝트 구조

반려동물 동반 가능 장소를 지도/목록으로 보여주는 앱.
**단일 Activity + Jetpack Compose Navigation, MVVM + Repository, Hilt(DI)** 기반.

## 계층 구조

```
com.kimdev.petmap
├── PetMapApplication.kt          # @HiltAndroidApp 진입점
├── MainActivity.kt               # 단일 Activity. 스플래시·테마·온보딩 분기·하단탭 Scaffold + NavHost
│
├── core/                         # 공통/인프라
│   ├── ads/       AdsConsent (UMP 동의 → AdMob 초기화)
│   ├── common/    Constants(지도 기본 카메라), Dispatchers(@IoDispatcher/@DefaultDispatcher)
│   ├── location/  LocationProvider (FusedLocation 래퍼)
│   ├── review/    InAppReview (실행 횟수 기반 Play 인앱 리뷰)
│   └── util/      IntentActions (전화·공유·네이버 길찾기·홈페이지 열기)
│
├── domain/                       # 순수 비즈니스 (안드로이드 의존성 없음)
│   ├── model/      Place·PetInfo·PlaceCategory·PetFilter, Region/RegionCount(지역 브라우징), GeoClusterCell(저줌 집계 셀)
│   ├── repository/ PlaceRepository (인터페이스)
│   └── util/       Geo(거리 계산·포맷), OpeningHours(운영시간 파싱 → 영업중 판정)
│
├── data/                         # 데이터 구현 (프리빌트 Room DB 단일 소스, 네트워크 없음)
│   ├── local/
│   │   ├── PetMapDatabase        # Room. exportSchema=true → app/schemas/ 에 JSON 커밋
│   │   ├── FavoriteBackup        # 즐겨찾기 미러(JSON, Auto Backup 대상) + 구버전 DB 즐겨찾기 보존
│   │   ├── PlaceFts              # FTS4 유니그램 색인 생성/질의 변환
│   │   ├── RecentSearchStore     # 최근 검색어 (SharedPreferences)
│   │   ├── ThemeStore            # 라이트/다크/시스템 테마 (SharedPreferences)
│   │   ├── PetProfileStore       # 내 반려견 체중 (SharedPreferences) → PetFilter.MY_PET 기준값
│   │   ├── dao/    PlaceDao(뷰포트·그리드 집계·FTS 검색), FavoriteDao, MetaDao
│   │   └── entity/ PlaceEntity(약 24,700행, 실내/실외 nullable, maxPetKg, sortKey), FavoriteEntity, MetaEntity(data_date 등 에셋 메타)
│   ├── mapper/     Entity ↔ Domain 변환
│   └── repository/ PlaceRepositoryImpl
│
├── di/                           # Hilt 모듈
│   DatabaseModule(createFromAsset + 버전 업 시 파괴적 재생성), DispatcherModule, RepositoryModule
│
└── ui/
    ├── navigation/ Destinations(Routes·TopLevelDestination), PetMapNavHost
    ├── map/        MapScreen·MapViewModel·Clustering(그리드 클러스터링)·MarkerIcons(핀 렌더링)
    ├── list/       ListScreen·ListViewModel     (검색/필터/정렬 목록)
    ├── favorite/   FavoriteScreen·FavoriteViewModel (즐겨찾기 + 실행취소)
    ├── detail/     DetailScreen·DetailViewModel (상세 + 미니맵 + 액션)
    ├── settings/   SettingsScreen·SettingsViewModel(테마), LicensesScreen(오픈소스 고지)
    ├── onboarding/ OnboardingScreen (최초 실행 + 설정에서 다시 보기)
    ├── components/ BannerAd, CategoryFilterRow, CategoryVisuals, EmptyState, PetWeightDialog(체중 입력, 설정·칩 공용), RegionSheet(시/도→시/군/구 선택),
│   │              TabScreenScaffold(목록·즐겨찾기 공통 골격), PetFilterChips, LocationPermission(ui/common)
    │               LocationSettingsDialog, PlaceCard, PlacePreviewSheet, SearchTextField
    ├── common/     SavedFilters (SavedStateHandle 필터 직렬화)
    └── theme/      Color, Theme, Type
```

## 화면 흐름

```
(최초 실행) 온보딩 ─→ 하단탭
                      [지도] ─┬─ (마커 탭) → 미리보기 바텀시트 ─→ [상세]
                      [목록] ─┼─ (카드 탭) ──────────────────────→ [상세] ─ (지도에서 보기) → [포커스 지도 mapFocus?focus=id] (push, 하단탭 숨김, 뒤로가기로 상세 복귀)
                      [즐겨찾기] ┘                                          ← 하단탭 숨김, 뒤로가기 복귀
                      [설정] ─→ 온보딩 다시 보기 · 라이선스 · 테마 전환
```

## 데이터 흐름 (단방향)

```
assets/petmap.db (프리빌트, v9·FTS·meta 동봉) ─ createFromAsset ─→ Room
                                                              │
Room(places + favorites) ─→ PlaceRepositoryImpl ─→ ViewModel(StateFlow) ─→ Compose UI
```

- **네트워크 경로 없음.** 데이터 갱신은 CSV → `tools/build_db.py` 재생성 → 앱 업데이트로만 이뤄진다
  (`/rebuild-db` 스킬 및 `CLAUDE.md` 참고).
- **버전 업 = 파괴적 재생성**: 마이그레이션 없이 `PetMapDatabase.VERSION`을 올리면 Room이 기존 파일을
  지우고 에셋을 재복사한다. 즐겨찾기는 `FavoriteBackup` 미러로 보존(`DatabaseModule`, open 전)·복원(`ensureSeeded`).
- 검색: FTS4 글자 단위 유니그램(한국어 부분검색 보존). 색인은 에셋에 동봉된다.
- 지도 조회 경로 2종: 고줌은 뷰포트 개별 로우 + 클라이언트 클러스터링(`ui/map/Clustering.kt`),
  저줌(zoom < 11)은 SQL 그리드 집계(`PlaceDao.getClusterCells`)로 셀별 개수만 가져온다.

## 모듈

- `:app` — 앱 본체.
- `:baselineprofile` — Baseline Profile 생성기(`com.android.test`). 앱의 `nonMinifiedRelease`를 기기에서
  구동해 시작·지도·목록·즐겨찾기·설정 경로를 수집 → `app/src/release/generated/baselineProfiles/`.
  앱 바이너리에는 프로필 파일만 들어가고 이 모듈은 실리지 않는다.

## 실행 전 설정 (local.properties)

```properties
naver.map.clientId=네이버클라우드_Maps_ClientID
```

- 장소 데이터는 앱에 내장되어 키가 없어도 목록/마커가 동작한다(지도 타일 렌더링에만 키 필요).

## 테스트

- 단위 테스트(`app/src/test/`): ViewModel(Map·List), 매퍼, FTS 문자열 변환, 클러스터링,
  거리/운영시간 유틸. `fake/`에 FakePlaceRepository 등 페이크 인프라.
- 계약 테스트(`app/src/androidTest/`): `PetMapDatabaseAssetTest` — 에셋 DB 로드(identity_hash 검증)
  + FTS 검색 + meta 기준일을 실기기에서 고정. `LegacyDatabaseRecreateTest` — 구버전 파일 재생성 시
  즐겨찾기 보존·복원. `build_db.py` 동기화 실수를 잡는 안전망.
- CI(`.github/workflows/ci.yml`): PR/main 푸시마다 단위 테스트 + Lint + R8 릴리스 빌드.

## 다음 확장 포인트

