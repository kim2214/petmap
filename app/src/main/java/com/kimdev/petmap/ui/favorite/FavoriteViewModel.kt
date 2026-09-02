package com.kimdev.petmap.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: PlaceRepository,
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
}
