package com.kimdev.petmap.domain.repository

import com.kimdev.petmap.domain.model.DataInfo
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.GeoBounds
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.PetFilter
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.model.Region
import com.kimdev.petmap.domain.model.RegionCount
import kotlinx.coroutines.flow.Flow

/**
 * 장소 데이터 접근 추상화.
 * 프리빌트 에셋 Room DB(assets/petmap.db)가 단일 소스다. 네트워크 갱신 없음.
 * 데이터 갱신은 CSV → tools/build_db.py 재생성 → 앱 업데이트(DB 버전 업 → 에셋 재복사)로만 이뤄진다.
 */
interface PlaceRepository {

    /** 첫 접근 시 에셋 DB 복사(또는 버전 업 재복사)가 일어나도록 워밍업하고, 비어 있는 즐겨찾기를 미러에서 복원 */
    suspend fun ensureSeeded()

    /** 내장 데이터 기준일·수록 장소 수 (설정 화면) */
    suspend fun getDataInfo(): DataInfo

    /** 즐겨찾기 메모 저장/수정. null 또는 빈 문자열이면 메모 삭제 */
    suspend fun updateFavoriteMemo(placeId: String, memo: String?)

    /**
     * 지도 뷰포트: 중심 좌표 기준 반경 박스 내 장소 (가까운 순, 개수 제한).
     * categories 비면 전체. petFilters 는 SQL 에서 AND 로 걸린다(정보 없는 장소 제외).
     * myPetKg 는 [PetFilter.MY_PET] 이 켜졌을 때 비교할 내 반려견 체중(등록 안 됐으면 null → 조건 없음).
     */
    suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory> = emptySet(),
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        limit: Int = 300,
    ): List<Place>

    /**
     * 저줌 지도 개요: 뷰포트를 [gridDivisions]×[gridDivisions] 격자로 나눠 셀별 개수만 집계.
     * 개별 로우/거리 정렬 없이 화면 전역을 저비용으로 커버한다.
     */
    suspend fun getClusterCells(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory> = emptySet(),
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        gridDivisions: Int,
        limit: Int,
    ): List<GeoClusterCell>

    /** 집계 셀 범위 재조회: 저줌 클러스터 탭 시 셀 안의 실제 장소를 목록으로 펼친다 (셀 중심 가까운 순) */
    suspend fun getPlacesInCell(
        cell: GeoClusterCell,
        categories: Set<PlaceCategory> = emptySet(),
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        limit: Int = 50,
    ): List<Place>

    /**
     * 이름/주소 검색 + 카테고리 다중 필터 (관련도·이름순). petFilters 는 SQL 에서 AND 로 걸린다.
     * - 검색어가 비면 카테고리·동반 조건·지역만으로 전체를 이름순 탐색한다(목록 화면 브라우징).
     * - region 을 주면 도로명주소가 그 시/도(·시/군/구)로 시작하는 장소만.
     * - 검색어가 있는데 색인 가능한 글자가 하나도 없으면(이모지·구두점만) **빈 목록** — 전체 폴백은
     *   가나다순 앞 N 건을 "검색 결과"로 보이게 해 오해를 낳는다.
     */
    suspend fun search(
        query: String,
        categories: Set<PlaceCategory> = emptySet(),
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        region: Region? = null,
        limit: Int = 200,
    ): List<Place>

    /**
     * 목록: 이름/주소 검색 + 카테고리 다중 필터, 사용자 위치에서 가까운 순 (각 항목에 거리 채움).
     * [radiusKm] 을 주면 그 반경(위경도 박스 근사) 밖은 제외한다 — "거리순"은 주변 탐색 의도라
     * 수백 km 떨어진 결과까지 나열하지 않는다. petFilters 는 [search] 와 같이 SQL 에서 AND 로 걸린다.
     */
    suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        radiusKm: Double? = null,
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        region: Region? = null,
        limit: Int = 200,
    ): List<Place>

    /**
     * 지역 브라우징용 시/도·시/군/구별 장소 개수. 시/군/구로 볼 수 없는 토큰(세종의 읍·면 등)은
     * sigungu = null 한 행으로 합쳐진다. 장소 데이터는 설치 동안 불변이므로 첫 호출 결과를 캐시한다.
     */
    suspend fun getRegionCounts(): List<RegionCount>

    /**
     * 지역 안 장소들이 들어가는 위/경도 범위 (지도 카메라 맞춤용). 장소가 없으면 null.
     * 지오코딩 오차로 튄 좌표가 범위를 전국으로 벌리지 않게, 표본이 충분하면 양 끝 2% 를 잘라낸다.
     */
    suspend fun getRegionBounds(region: Region): GeoBounds?

    suspend fun getPlace(id: String): Place?

    fun observeFavorites(): Flow<List<Place>>
    fun observeFavoriteIds(): Flow<Set<String>>

    /**
     * 하트 토글. 해제하면 favorites 행이 지워져 메모도 함께 사라진다 — 실행취소가 필요한 곳은
     * [removeFavorite]/[restoreFavorite] 를 쓴다.
     */
    suspend fun toggleFavorite(place: Place)

    /**
     * 즐겨찾기 삭제. 실행취소용으로 삭제 직전 상태(메모·추가 시각 포함)를 돌려준다.
     * 즐겨찾기가 아니었으면(이미 지워짐 등) 아무것도 하지 않고 null.
     */
    suspend fun removeFavorite(placeId: String): FavoriteSnapshot?

    /** [removeFavorite] 가 돌려준 스냅샷을 그대로 되살린다 (메모·순서 보존) */
    suspend fun restoreFavorite(snapshot: FavoriteSnapshot)
}
