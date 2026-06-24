package com.example.petmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petmap.core.location.LocationProvider
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

data class MapUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val selectedCategory: PlaceCategory? = null,
    val error: String? = null,
) {
    val visiblePlaces: List<Place>
        get() = selectedCategory?.let { c -> places.filter { it.category == c } } ?: places
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadPlaces()
        observeFavorites()
    }

    private fun loadPlaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getPlaces() }
                .onSuccess { places -> _uiState.update { it.copy(isLoading = false, places = places) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(places = it.places.withFavorites(ids)) }
            }
        }
    }

    fun selectCategory(category: PlaceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun retry() = loadPlaces()
}
