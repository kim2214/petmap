package com.kimdev.petmap.fake

import com.kimdev.petmap.domain.model.DataInfo
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.PetFilter
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import kotlinx.coroutines.CompletableDeferred
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
    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    val memos = mutableMapOf<String, String?>()

    // 호출 기록
    var ensureSeededCount = 0
    var searchCount = 0
    var searchNearbyCount = 0
    var lastSearchLimit: Int? = null
    var lastSearchNearbyLimit: Int? = null
    var lastSearchNearbyRadiusKm: Double? = null
    var lastCategories: Set<PlaceCategory>? = null
    var lastPetFilters: Set<PetFilter>? = null
    var lastBoundsLimit: Int? = null
    var lastClusterCellsLimit: Int? = null

    fun setFavorites(ids: Set<String>) { favorites.value = ids }

    private fun List<Place>.filterBy(
        query: String,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
    ): List<Place> =
        filter { p ->
            (query.isBlank() || p.name.contains(query, ignoreCase = true)) &&
                (categories.isEmpty() || p.category in categories) &&
                petFilters.all { it.matches(p, myPetKg) }
        }

    /** 설정하면 ensureSeeded 가 이 예외를 던진다 (에셋 DB 준비 실패 시뮬레이션) */
    var seedFailure: Throwable? = null

    override suspend fun ensureSeeded() {
        ensureSeededCount++
        seedFailure?.let { throw it }
    }

    override suspend fun getDataInfo(): DataInfo = DataInfo(dataDate = null, placeCount = dataset.size)

    override suspend fun updateFavoriteMemo(placeId: String, memo: String?) {
        memos[placeId] = memo?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        limit: Int,
    ): List<Place> {
        lastCategories = categories
        lastPetFilters = petFilters
        lastBoundsLimit = limit
        return dataset.filterBy("", categories, petFilters, myPetKg).take(limit)
    }

    override suspend fun getClusterCells(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        gridDivisions: Int,
        limit: Int,
    ): List<GeoClusterCell> {
        lastCategories = categories
        lastPetFilters = petFilters
        lastClusterCellsLimit = limit
        // 소수 1자리 그리드로 묶어 셀별 개수 집계(테스트용 근사)
        return dataset.filterBy("", categories, petFilters, myPetKg)
            .groupBy { Math.round(it.lat * 10) / 10.0 to Math.round(it.lng * 10) / 10.0 }
            .map { (key, members) ->
                GeoClusterCell(
                    lat = key.first, lng = key.second, count = members.size,
                    minLat = key.first - 0.05, maxLat = key.first + 0.05,
                    minLng = key.second - 0.05, maxLng = key.second + 0.05,
                )
            }
            .take(limit)
    }

    override suspend fun getPlacesInCell(
        cell: GeoClusterCell,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        limit: Int,
    ): List<Place> {
        lastCategories = categories
        lastPetFilters = petFilters
        return dataset.filterBy("", categories, petFilters, myPetKg)
            .filter { it.lat in cell.minLat..cell.maxLat && it.lng in cell.minLng..cell.maxLng }
            .take(limit)
    }

    /** 설정하면 search 가 완료될 때까지 suspend 한다 (진행 중 취소 시나리오 재현용) */
    var searchGate: CompletableDeferred<Unit>? = null
    var lastSearchPetFilters: Set<PetFilter>? = null

    override suspend fun search(
        query: String,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        limit: Int,
    ): List<Place> {
        searchCount++
        lastSearchLimit = limit
        lastCategories = categories
        lastSearchPetFilters = petFilters
        searchGate?.await()
        return dataset.filterBy(query, categories, petFilters, myPetKg).take(limit)
    }

    override suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        radiusKm: Double?,
        limit: Int,
    ): List<Place> {
        searchNearbyCount++
        lastSearchNearbyLimit = limit
        lastSearchNearbyRadiusKm = radiusKm
        lastCategories = categories
        return dataset.filterBy(query, categories)
            .map { it.copy(distanceMeters = distanceMeters(userLat, userLng, it.lat, it.lng)) }
            .filter { radiusKm == null || (it.distanceMeters ?: 0.0) <= radiusKm * 1000 }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    override suspend fun getPlace(id: String): Place? = dataset.find { it.id == id }

    override fun observeFavorites(): Flow<List<Place>> =
        favorites.map { ids -> dataset.filter { it.id in ids } }

    override fun observeFavoriteIds(): Flow<Set<String>> = favorites

    override suspend fun toggleFavorite(place: Place) {
        favorites.value = if (place.id in favorites.value) favorites.value - place.id
        else favorites.value + place.id
    }

    override suspend fun removeFavorite(placeId: String): FavoriteSnapshot? {
        if (placeId !in favorites.value) return null
        val place = dataset.find { it.id == placeId } ?: return null
        favorites.value = favorites.value - placeId
        return FavoriteSnapshot(place.copy(isFavorite = true, memo = memos.remove(placeId)), addedAt = 0L)
    }

    override suspend fun restoreFavorite(snapshot: FavoriteSnapshot) {
        favorites.value = favorites.value + snapshot.place.id
        memos[snapshot.place.id] = snapshot.place.memo
    }
}
