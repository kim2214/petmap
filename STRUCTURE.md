# PetMap 프로젝트 구조

반려동물 동반 가능 장소를 지도에서 보여주는 앱.
**단일 Activity + Jetpack Compose Navigation, MVVM + Repository, Hilt(DI)** 기반.

## 계층 구조

```
com.kimdev.petmap
├── PetMapApplication.kt          # @HiltAndroidApp 진입점
├── MainActivity.kt               # 단일 Activity. 하단탭 Scaffold + NavHost
│
├── core/                         # 공통/인프라
│   ├── common/   Resource, Constants
│   └── location/ LocationProvider (FusedLocation 래퍼)
│
├── domain/                       # 순수 비즈니스 (안드로이드 의존성 없음)
│   ├── model/      Place, PetInfo, PlaceCategory
│   └── repository/ PlaceRepository (인터페이스)
│
├── data/                         # 데이터 구현
│   ├── remote/   api(PublicDataApi) + dto(PlaceResponse)
│   ├── local/    Room: PetMapDatabase, FavoriteDao, FavoriteEntity
│   ├── mapper/   DTO ↔ Domain ↔ Entity 변환
│   ├── sample/   SamplePlaces (키 미설정 시 폴백 더미)
│   └── repository/ PlaceRepositoryImpl
│
├── di/                           # Hilt 모듈
│   NetworkModule, DatabaseModule, RepositoryModule
│
└── ui/
    ├── navigation/ Routes, TopLevelDestination, PetMapNavHost
    ├── map/        MapScreen + MapViewModel       (네이버 지도 + 마커)
    ├── list/       ListScreen + ListViewModel     (검색/필터 목록)
    ├── favorite/   FavoriteScreen + FavoriteViewModel
    ├── detail/     DetailScreen + DetailViewModel (장소 상세)
    ├── components/ PlaceCard, CategoryFilterRow
    └── theme/      (기존 Material3 테마)
```

## 화면 흐름

```
[지도] ─┐
[목록] ─┼─→ (장소 탭) ─→ [상세]  ← 하단탭 숨김, 뒤로가기로 복귀
[즐겨찾기] ┘
```

## 데이터 흐름 (단방향)

```
공공데이터 API ─┐
                ├─→ PlaceRepositoryImpl ─→ ViewModel(StateFlow) ─→ Compose UI
Room(즐겨찾기) ─┘
```

## 실행 전 설정 (local.properties)

```properties
naver.map.clientId=네이버클라우드_Maps_ClientID
public.data.serviceKey=공공데이터포털_서비스키
```

- 키가 없어도 `SamplePlaces` 더미 데이터로 앱이 동작합니다(지도 타일은 키 필요).
- `data/remote/api/PublicDataApi.kt` 의 엔드포인트(uddi)와
  `data/remote/dto/PlaceResponse.kt` 의 `@SerialName` 컬럼명을
  실제 사용하는 데이터셋에 맞게 조정하세요.

## 다음 확장 포인트

- 위치 권한 요청 UI (accompanist-permissions 의존성 추가됨) → 내 위치 카메라 이동
- 마커 클릭 시 바텀시트로 미리보기
- 지도 영역(bounds) 기반 서버 페이징
- 거리순 정렬, 영업중 필터
- 다중 데이터셋(카페/식당/숙박) 통합
```
