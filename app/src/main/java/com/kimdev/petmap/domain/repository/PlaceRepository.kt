package com.kimdev.petmap.domain.repository

import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import kotlinx.coroutines.flow.Flow

/**
 * 장소 데이터 접근 추상화.
 * 프리빌트 에셋 Room DB(assets/petmap.db)가 단일 소스다. 네트워크 갱신 없음.
 * 데이터 갱신은 CSV → tools/build_db.py 재생성 → 앱 업데이트로만 이뤄진다.
 */
interface PlaceRepository {

    /** 첫 접근 시 에셋 DB 복사가 일어나도록 워밍업 (별도 시딩 없음) */
    suspend fun ensureSeeded()

    /** 지도 뷰포트: 중심 좌표 기준 반경 박스 내 장소 (가까운 순, 개수 제한). categories 비면 전체 */
    suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory> = emptySet(),
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
        gridDivisions: Int,
        limit: Int,
    ): List<GeoClusterCell>

    /** 집계 셀 범위 재조회: 저줌 클러스터 탭 시 셀 안의 실제 장소를 목록으로 펼친다 (셀 중심 가까운 순) */
    suspend fun getPlacesInCell(
        cell: GeoClusterCell,
        categories: Set<PlaceCategory> = emptySet(),
        limit: Int = 50,
    ): List<Place>

    /** 목록: 이름/주소 검색 + 카테고리 다중 필터 (이름순) */
    suspend fun search(
        query: String,
        categories: Set<PlaceCategory> = emptySet(),
        limit: Int = 200,
    ): List<Place>

    /** 목록: 이름/주소 검색 + 카테고리 다중 필터, 사용자 위치에서 가까운 순 (각 항목에 거리 채움) */
    suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        limit: Int = 200,
    ): List<Place>

    suspend fun getPlace(id: String): Place?

    fun observeFavorites(): Flow<List<Place>>
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun toggleFavorite(place: Place)
}
