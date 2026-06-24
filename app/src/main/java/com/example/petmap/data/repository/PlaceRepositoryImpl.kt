package com.example.petmap.data.repository

import com.example.petmap.BuildConfig
import com.example.petmap.data.local.dao.FavoriteDao
import com.example.petmap.data.mapper.toDomain
import com.example.petmap.data.mapper.toFavoriteEntity
import com.example.petmap.data.remote.api.PublicDataApi
import com.example.petmap.data.sample.SamplePlaces
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val api: PublicDataApi,
    private val favoriteDao: FavoriteDao,
) : PlaceRepository {

    // 원격에서 받아온 장소를 메모리에 캐시하여 상세 조회에 사용한다.
    private var cache: List<Place> = emptyList()

    override suspend fun getPlaces(query: String?): List<Place> {
        val places = fetchRemoteOrSample()
        cache = places
        return if (query.isNullOrBlank()) {
            places
        } else {
            places.filter { it.name.contains(query, true) || it.roadAddress.contains(query, true) }
        }
    }

    override suspend fun getPlace(id: String): Place? {
        if (cache.isEmpty()) cache = fetchRemoteOrSample()
        return cache.firstOrNull { it.id == id }
    }

    override fun observeFavorites(): Flow<List<Place>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { it.toSet() }

    override suspend fun toggleFavorite(place: Place) {
        if (favoriteDao.exists(place.id)) {
            favoriteDao.deleteById(place.id)
        } else {
            favoriteDao.insert(place.toFavoriteEntity())
        }
    }

    /**
     * 실제 키가 설정돼 있으면 OpenAPI 호출, 아니면(또는 실패 시) 샘플 데이터로 폴백.
     * 스캐폴딩 단계에서 키 없이도 앱이 동작하도록 한다.
     */
    private suspend fun fetchRemoteOrSample(): List<Place> {
        val key = BuildConfig.PUBLIC_DATA_SERVICE_KEY
        if (key.isBlank() || key == "YOUR_PUBLIC_DATA_SERVICE_KEY") {
            return SamplePlaces.list
        }
        return runCatching {
            api.getPetFriendlyPlaces(serviceKey = key)
                .data
                .mapNotNull { it.toDomain() }
        }.getOrElse { SamplePlaces.list }
    }
}

/** 즐겨찾기 상태를 장소 목록에 결합하는 확장 (ViewModel 에서 사용) */
fun List<Place>.withFavorites(favoriteIds: Set<String>): List<Place> =
    map { it.copy(isFavorite = it.id in favoriteIds) }
