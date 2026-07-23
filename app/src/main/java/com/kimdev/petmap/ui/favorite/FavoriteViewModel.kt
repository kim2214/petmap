package com.kimdev.petmap.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoriteUiState(
    /** 카테고리 필터가 적용된 목록 */
    val favorites: List<Place> = emptyList(),
    val selectedCategories: Set<PlaceCategory> = emptySet(),
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

    val uiState: StateFlow<FavoriteUiState> =
        combine(repository.observeFavorites(), selectedCategories) { favorites, selected ->
            FavoriteUiState(
                favorites = if (selected.isEmpty()) favorites
                else favorites.filter { it.category in selected },
                selectedCategories = selected,
                totalCount = favorites.size,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FavoriteUiState(),
        )

    fun toggleCategory(category: PlaceCategory) = selectedCategories.update {
        if (category in it) it - category else it + category
    }

    fun clearCategories() {
        selectedCategories.value = emptySet()
    }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }
}
