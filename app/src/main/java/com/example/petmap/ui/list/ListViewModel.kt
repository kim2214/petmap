package com.example.petmap.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petmap.data.repository.withFavorites
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.model.PlaceCategory
import com.example.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val query: String = "",
    val selectedCategory: PlaceCategory? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    private var favoriteIds: Set<String> = emptySet()

    init {
        // 검색어/카테고리 변경을 디바운스하여 조회
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState
                .map { it.query to it.selectedCategory }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (query, category) -> runSearch(query, category) }
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                favoriteIds = ids
                _uiState.update { it.copy(places = it.places.withFavorites(ids)) }
            }
        }
    }

    private suspend fun runSearch(query: String, category: PlaceCategory?) {
        _uiState.update { it.copy(isLoading = true) }
        val results = repository.search(query, category).withFavorites(favoriteIds)
        _uiState.update { it.copy(isLoading = false, places = results) }
    }

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun selectCategory(category: PlaceCategory?) =
        _uiState.update { it.copy(selectedCategory = category) }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }
}
