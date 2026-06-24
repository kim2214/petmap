package com.example.petmap.domain.repository

import com.example.petmap.domain.model.Place
import kotlinx.coroutines.flow.Flow

/**
 * 장소 데이터 접근 추상화. 구현체(data 계층)는 원격 API + 로컬 DB 를 조합한다.
 * UI/ViewModel 은 이 인터페이스에만 의존한다.
 */
interface PlaceRepository {

    /** 지정 영역(또는 전체) 의 반려동물 동반 가능 장소 조회 */
    suspend fun getPlaces(query: String? = null): List<Place>

    /** 단일 장소 상세 */
    suspend fun getPlace(id: String): Place?

    /** 즐겨찾기한 장소 목록 (실시간 관찰) */
    fun observeFavorites(): Flow<List<Place>>

    suspend fun toggleFavorite(place: Place)

    fun observeFavoriteIds(): Flow<Set<String>>
}
