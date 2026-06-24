package com.example.petmap.data.repository

import android.util.Log
import com.example.petmap.BuildConfig
import com.example.petmap.data.csv.AssetPlaceSeeder
import com.example.petmap.data.local.SyncPreferences
import com.example.petmap.data.local.dao.FavoriteDao
import com.example.petmap.data.local.dao.PlaceDao
import com.example.petmap.data.mapper.toDomain
import com.example.petmap.data.mapper.toEntity
import com.example.petmap.data.mapper.toFavoriteEntity
import com.example.petmap.data.remote.api.PublicDataApi
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.model.PlaceCategory
import com.example.petmap.domain.repository.PlaceRepository
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
    private val seeder: AssetPlaceSeeder,
    private val syncPrefs: SyncPreferences,
) : PlaceRepository {

    override suspend fun ensureSeeded() {
        seeder.seedIfEmpty()
    }

    override suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        category: PlaceCategory?,
        limit: Int,
    ): List<Place> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * max(0.1, cos(Math.toRadians(centerLat))))
        return placeDao.getInBounds(
            minLat = centerLat - latDelta,
            maxLat = centerLat + latDelta,
            minLng = centerLng - lngDelta,
            maxLng = centerLng + lngDelta,
            centerLat = centerLat,
            centerLng = centerLng,
            category = category?.name,
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun search(query: String, category: PlaceCategory?, limit: Int): List<Place> =
        placeDao.search(query.trim(), category?.name, limit).map { it.toDomain() }

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
