package com.kimdev.petmap.fake

import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 메모리 기반 PlaceRepository 페이크.
 * - [dataset] 을 단일 소스로 사용해 search/getPlacesInBounds 가 카테고리·질의로 필터링.
 * - 어떤 메서드가 어떤 인자로 호출됐는지 기록해 ViewModel 의 분기 로직을 검증한다.
 */
class FakePlaceRepository : PlaceRepository {

    var dataset: List<Place> = emptyList()
    var refreshResult: Boolean = false
    private val favorites = MutableStateFlow<Set<String>>(emptySet())

    // 호출 기록
    var ensureSeededCount = 0
    var searchCount = 0
    var searchNearbyCount = 0
    var lastSearchLimit: Int? = null
    var lastSearchNearbyLimit: Int? = null
    var lastCategories: Set<PlaceCategory>? = null
    var lastBoundsLimit: Int? = null
    var lastClusterCellsLimit: Int? = null

    fun setFavorites(ids: Set<String>) { favorites.value = ids }

    private fun List<Place>.filterBy(query: String, categories: Set<PlaceCategory>): List<Place> =
        filter { p ->
            (query.isBlank() || p.name.contains(query, ignoreCase = true)) &&
                (categories.isEmpty() || p.category in categories)
        }

    override suspend fun ensureSeeded() { ensureSeededCount++ }

    override suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        limit: Int,
    ): List<Place> {
        lastCategories = categories
        lastBoundsLimit = limit
        return dataset.filterBy("", categories).take(limit)
    }

    override suspend fun getClusterCells(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        gridDivisions: Int,
        limit: Int,
    ): List<GeoClusterCell> {
        lastCategories = categories
        lastClusterCellsLimit = limit
        // 소수 1자리 그리드로 묶어 셀별 개수 집계(테스트용 근사)
        return dataset.filterBy("", categories)
            .groupBy { Math.round(it.lat * 10) / 10.0 to Math.round(it.lng * 10) / 10.0 }
            .map { (key, members) -> GeoClusterCell(key.first, key.second, members.size) }
            .take(limit)
    }

    override suspend fun search(query: String, categories: Set<PlaceCategory>, limit: Int): List<Place> {
        searchCount++
        lastSearchLimit = limit
        lastCategories = categories
        return dataset.filterBy(query, categories).take(limit)
    }

    override suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        limit: Int,
    ): List<Place> {
        searchNearbyCount++
        lastSearchNearbyLimit = limit
        lastCategories = categories
        return dataset.filterBy(query, categories).take(limit)
            .map { it.copy(distanceMeters = distanceMeters(userLat, userLng, it.lat, it.lng)) }
    }

    override suspend fun getPlace(id: String): Place? = dataset.find { it.id == id }

    override fun observeFavorites(): Flow<List<Place>> =
        favorites.map { ids -> dataset.filter { it.id in ids } }

    override fun observeFavoriteIds(): Flow<Set<String>> = favorites

    override suspend fun toggleFavorite(place: Place) {
        favorites.value = if (place.id in favorites.value) favorites.value - place.id
        else favorites.value + place.id
    }

    override suspend fun refreshFromRemoteIfStale(now: Long): Boolean = refreshResult
}
