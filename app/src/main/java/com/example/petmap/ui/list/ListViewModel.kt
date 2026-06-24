package com.example.petmap.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petmap.data.repository.withFavorites
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.model.PlaceCategory
import com.example.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val query: String = "",
    val selectedCategory: PlaceCategory? = null,
) {
    val visiblePlaces: List<Place>
        get() = places
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { query.isBlank() || it.name.contains(query, true) || it.roadAddress.contains(query, true) }
}

@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(places = it.places.withFavorites(ids)) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val places = runCatching { repository.getPlaces() }.getOrDefault(emptyList())
            _uiState.update { it.copy(isLoading = false, places = places) }
        }
    }

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun selectCategory(category: PlaceCategory?) =
        _uiState.update { it.copy(selectedCategory = category) }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }
}
