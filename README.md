# 🐾 PetMap

지도를 이용해 **반려동물과 함께 이용 가능한 카페·식당·시설**을 찾아주는 Android 앱.
공공데이터 및 민간 데이터를 활용해 반려동물 동반 가능 장소를 지도와 목록으로 보여줍니다.

> 현재 상태: **기본 구조(스캐폴딩) 완료** — 빌드/설치/실행 검증됨. API 키 미설정 시 샘플 데이터로 동작합니다.

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🗺️ 지도 | 네이버 지도 위에 반려동물 동반 가능 장소를 마커로 표시, 카테고리 필터 |
| 📋 목록 | 장소 검색 + 카테고리 필터 + 즐겨찾기 토글 |
| ❤️ 즐겨찾기 | 관심 장소를 로컬(Room)에 저장 |
| 📄 상세 | 주소·전화·운영시간·휴무일과 반려동물 동반 정책(크기/실내외/제한사항) |

## 🛠️ 기술 스택

- **언어/UI**: Kotlin, Jetpack Compose, Material3
- **아키텍처**: 단일 Activity + Compose Navigation, MVVM + Repository, 계층 분리(`data` / `domain` / `ui`)
- **DI**: Hilt
- **네트워크**: Retrofit + kotlinx.serialization (공공데이터포털 OpenAPI)
- **로컬 DB**: Room (즐겨찾기)
- **지도**: 네이버 지도 SDK (`naver-map-compose`)
- **위치**: Google Play Services FusedLocationProvider
- **이미지**: Coil

## 📁 프로젝트 구조

```
com.example.petmap
├── PetMapApplication.kt       # @HiltAndroidApp 진입점
├── MainActivity.kt            # 단일 Activity (하단탭 Scaffold + NavHost)
│
├── core/                      # 공통/인프라 (Resource, Constants, LocationProvider)
├── domain/                    # 순수 비즈니스 (모델 + Repository 인터페이스)
├── data/                      # 데이터 구현 (remote API / local Room / mapper / repository)
├── di/                        # Hilt 모듈 (Network / Database / Repository)
└── ui/                        # 화면 + ViewModel (map / list / favorite / detail / components)
```

> 더 자세한 계층 설명과 데이터 흐름은 [`STRUCTURE.md`](./STRUCTURE.md) 참고.

## 🚀 시작하기

### 요구 사항
- Android Studio (최신 버전 권장)
- JDK 17+
- minSdk 30 / targetSdk 36

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
# 공공데이터포털(data.go.kr) 서비스 키 (URL Decoded)
public.data.serviceKey=YOUR_PUBLIC_DATA_SERVICE_KEY
```

- **장소 데이터는 앱에 내장**되어 있어 두 키가 없어도 목록/지도 마커가 표시됩니다. 단, **지도 타일 렌더링에는 네이버 키가 필요**하고, `public.data.serviceKey`는 하이브리드 갱신에만 쓰입니다.
- 네이버 SDK 버전에 따라 매니페스트의 메타데이터 키 이름이 다릅니다
  (신규 콘솔: `com.naver.maps.map.NCP_KEY_ID` / 구형 콘솔: `com.naver.maps.map.CLIENT_ID`).

### 3. 빌드 & 실행
```bash
./gradlew :app:assembleDebug      # APK 빌드
# 또는 Android Studio에서 Sync 후 Run ▶
```

## 🗃️ 데이터 전략 (내장 CSV + 하이브리드 갱신)

API 통신 부담을 최소화하기 위해 **내장 CSV를 단일 소스로** 사용한다.

- `app/src/main/assets/places.csv` — 한국문화정보원 "전국 반려동물 동반 가능 문화시설" 데이터셋(약 7만 행). APK 패키징 시 zip 압축되어 용량 영향은 ~2.4MB.
- **최초 실행 시 1회** Room(`places` 테이블)에 시딩. 원본에 동일 레코드가 대량 중복돼 있어 `이름+좌표` 기준으로 정제하면 **약 23,925개 고유 장소**가 된다.
- 지도/목록/검색은 모두 **로컬 Room 쿼리**로 처리 → 위치 기반 조회 시 네트워크 호출 0.
- **하이브리드 갱신**: 마지막 동기화가 오래됐고(`SyncPreferences`, 기본 7일) 서비스 키가 있으면 백그라운드에서 공공데이터 API로 Room을 upsert.

데이터셋을 새로 받으면 `places.csv`만 교체하면 된다(헤더명 기반 매핑이라 견고). API 연동부를 실제로 쓰려면:
- `data/remote/api/PublicDataApi.kt` — 엔드포인트(데이터셋 UUID), 쿼리 파라미터
- `data/remote/dto/PlaceResponse.kt` — 응답 컬럼명(`@SerialName`)

## 🗺️ 로드맵

- [x] 내장 CSV 시딩 + Room 단일 소스
- [x] 지도 뷰포트(반경) 기반 로컬 조회
- [x] 하이브리드 갱신 plumbing (`refreshFromRemoteIfStale`)
- [x] 위치 권한 요청 + 내 위치로 카메라 이동(네이버 위치 오버레이 + Follow)
- [ ] 실제 공공데이터 API 연동 (엔드포인트/필드 확정)
- [ ] 마커 클러스터링 (도심 밀집 대응)
- [ ] 마커 클릭 시 바텀시트 미리보기
- [ ] 거리순 정렬, 영업중 필터

## 📄 라이선스

미정.
