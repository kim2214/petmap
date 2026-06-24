package com.kimdev.petmap.domain.repository

import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import kotlinx.coroutines.flow.Flow

/**
 * 장소 데이터 접근 추상화.
 * 데이터는 내장 CSV로 시딩된 Room 을 단일 소스로 사용하고, API 로 주기적 갱신(하이브리드)한다.
 */
interface PlaceRepository {

    /** 내장 데이터가 비어 있으면 최초 1회 시딩 */
    suspend fun ensureSeeded()

    /** 지도 뷰포트: 중심 좌표 기준 반경 박스 내 장소 (가까운 순, 개수 제한) */
    suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        category: PlaceCategory? = null,
        limit: Int = 300,
    ): List<Place>

    /** 목록: 이름/주소 검색 + 카테고리 필터 (이름순) */
    suspend fun search(
        query: String,
        category: PlaceCategory? = null,
        limit: Int = 200,
    ): List<Place>

    /** 목록: 이름/주소 검색 + 카테고리 필터, 사용자 위치에서 가까운 순 (각 항목에 거리 채움) */
    suspend fun searchNearby(
        query: String,
        category: PlaceCategory?,
        userLat: Double,
        userLng: Double,
        limit: Int = 200,
    ): List<Place>

    suspend fun getPlace(id: String): Place?

    fun observeFavorites(): Flow<List<Place>>
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun toggleFavorite(place: Place)

    /** 하이브리드: 마지막 동기화가 오래됐고 키가 설정돼 있으면 API 로 갱신. 갱신 수행 시 true */
    suspend fun refreshFromRemoteIfStale(now: Long): Boolean
}
