package com.kimdev.petmap.data.repository

import android.util.Log
import com.kimdev.petmap.BuildConfig
import com.kimdev.petmap.data.local.SyncPreferences
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.mapper.toDomain
import com.kimdev.petmap.data.mapper.toEntity
import com.kimdev.petmap.data.mapper.toFavoriteEntity
import com.kimdev.petmap.data.remote.api.PublicDataApi
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.cos
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val api: PublicDataApi,
    private val placeDao: PlaceDao,
    private val favoriteDao: FavoriteDao,
    private val syncPrefs: SyncPreferences,
) : PlaceRepository {

    /**
     * DB 는 에셋(미리 시딩됨)에서 로드되므로 별도 시딩이 필요 없다.
     * 첫 접근 시 에셋 복사가 일어나도록 가벼운 쿼리로 워밍업만 한다.
     */
    override suspend fun ensureSeeded() {
        runCatching { placeDao.count() }
    }

    // 빈 카테고리 셋이면 IN () 오류 방지를 위해 더미 1개 + catCount=0 으로 전달
    private fun catsOf(categories: Set<PlaceCategory>): Pair<List<String>, Int> =
        if (categories.isEmpty()) listOf("") to 0
        else categories.map { it.name } to categories.size

    override suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        limit: Int,
    ): List<Place> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * max(0.1, cos(Math.toRadians(centerLat))))
        val (cats, catCount) = catsOf(categories)
        return placeDao.getInBounds(
            minLat = centerLat - latDelta,
            maxLat = centerLat + latDelta,
            minLng = centerLng - lngDelta,
            maxLng = centerLng + lngDelta,
            centerLat = centerLat,
            centerLng = centerLng,
            cats = cats,
            catCount = catCount,
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun search(query: String, categories: Set<PlaceCategory>, limit: Int): List<Place> {
        val (cats, catCount) = catsOf(categories)
        return placeDao.search(query.trim(), cats, catCount, limit).map { it.toDomain() }
    }

    override suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        limit: Int,
    ): List<Place> {
        val (cats, catCount) = catsOf(categories)
        return placeDao.searchByDistance(query.trim(), cats, catCount, userLat, userLng, limit)
            .map { it.toDomain().copy(distanceMeters = distanceMeters(userLat, userLng, it.lat, it.lng)) }
    }

    override suspend fun getPlace(id: String): Place? = placeDao.getById(id)?.toDomain()

    override fun observeFavorites(): Flow<List<Place>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { it.toSet() }

    override suspend fun toggleFavorite(place: Place) {
        if (favoriteDao.exists(place.id)) favoriteDao.deleteById(place.id)
        else favoriteDao.insert(place.toFavoriteEntity())
    }

    override suspend fun refreshFromRemoteIfStale(now: Long): Boolean {
        val key = BuildConfig.PUBLIC_DATA_SERVICE_KEY
        if (key.isBlank() || key == "YOUR_PUBLIC_DATA_SERVICE_KEY") return false
        if (!syncPrefs.isStale(now)) return false
        return runCatching {
            val entities = api.getPetFriendlyPlaces(serviceKey = key).data.mapNotNull { it.toEntity() }
            if (entities.isNotEmpty()) {
                entities.chunked(1000).forEach { placeDao.upsertAll(it) }
                syncPrefs.lastSyncAt = now
                Log.i(TAG, "Remote refresh upserted ${entities.size} places")
            }
            true
        }.getOrElse {
            Log.w(TAG, "Remote refresh failed: ${it.message}")
            false
        }
    }

    companion object {
        private const val TAG = "PlaceRepository"
    }
}

/** 장소 목록에 즐겨찾기 상태를 결합 (ViewModel 에서 사용) */
fun List<Place>.withFavorites(favoriteIds: Set<String>): List<Place> =
    map { it.copy(isFavorite = it.id in favoriteIds) }
