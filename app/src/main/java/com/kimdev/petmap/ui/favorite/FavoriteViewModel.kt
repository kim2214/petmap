package com.kimdev.petmap.ui.favorite

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoriteUiState(
    /** 카테고리·검색어 필터가 적용된 목록 */
    val favorites: List<Place> = emptyList(),
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    val query: String = "",
    /** 필터와 무관한 전체 즐겨찾기 수. 0 이면 "아직 없음", >0 인데 favorites 가 비면 "필터 결과 없음" */
    val totalCount: Int = 0,
    /** 첫 emit 전(초기값)엔 true. 데이터가 오기 전 "즐겨찾기 없음" 이 잘못 깜빡이는 것을 막는다. */
    val isLoading: Boolean = true,
)

data class RecentUiState(
    /** 최근 본 순서(최신 우선). 하트 상태는 즐겨찾기와 동기화된다 */
    val places: List<Place> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val recentPlaceStore: RecentPlaceStore,
) : ViewModel() {

    private val selectedCategories = MutableStateFlow<Set<PlaceCategory>>(emptySet())
    private val query = MutableStateFlow("")

    val uiState: StateFlow<FavoriteUiState> =
        combine(repository.observeFavorites(), selectedCategories, query) { favorites, selected, q ->
            // 로컬 목록(수십~수백 건)이라 DB 검색 없이 이름/주소 부분 일치로 충분하다
            val filtered = favorites
                .filter { selected.isEmpty() || it.category in selected }
                .filter {
                    q.isBlank() || it.name.contains(q, ignoreCase = true) ||
                        it.roadAddress.contains(q, ignoreCase = true)
                }
            FavoriteUiState(
                favorites = filtered,
                selectedCategories = selected,
                query = q,
                totalCount = favorites.size,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FavoriteUiState(),
        )

    /**
     * 최근 본 장소. ID 목록이 바뀔 때만 DB 를 조회하고(최대 30건 IN 조회), 하트는 즐겨찾기 ID 로 덧입힌다.
     * 조회 실패(에셋 DB 준비 실패 등)는 빈 목록으로 — 즐겨찾기 탭 전체를 에러로 만들 일은 아니다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentState: StateFlow<RecentUiState> =
        combine(
            recentPlaceStore.recent.mapLatest { ids ->
                try {
                    repository.getPlaces(ids)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "recent places load failed: ${e.message}")
                    emptyList()
                }
            },
            repository.observeFavoriteIds(),
        ) { places, favoriteIds ->
            RecentUiState(places = places.map { it.copy(isFavorite = it.id in favoriteIds) }, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecentUiState(),
        )

    /** 최근 본 장소에서 하트 — 즐겨찾기 목록과 달리 카드가 사라지지 않으므로 단순 토글 */
    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    fun clearRecent() = recentPlaceStore.clear()

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun toggleCategory(category: PlaceCategory) = selectedCategories.update {
        if (category in it) it - category else it + category
    }

    fun clearCategories() {
        selectedCategories.value = emptySet()
    }

    /**
     * 즐겨찾기 삭제. 실행취소용 스냅샷을 돌려준다(이미 지워졌으면 null).
     * 화면 스코프가 아닌 viewModelScope 에서 실행해, 스낵바 도중 화면을 떠나도 삭제가 끊기지 않는다.
     */
    suspend fun removeFavorite(placeId: String): FavoriteSnapshot? =
        viewModelScope.async { repository.removeFavorite(placeId) }.await()

    /** 실행취소: 메모·추가 시각까지 삭제 직전 상태로 되살린다 */
    fun restoreFavorite(snapshot: FavoriteSnapshot) {
        viewModelScope.launch { repository.restoreFavorite(snapshot) }
    }

    private companion object {
        const val TAG = "FavoriteViewModel"
    }
}
