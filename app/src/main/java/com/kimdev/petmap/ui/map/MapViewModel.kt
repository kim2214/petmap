package com.kimdev.petmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val isSeeding: Boolean = true,
    val isLoading: Boolean = false,
    val clusters: List<MapCluster> = emptyList(),
    val selectedCategory: PlaceCategory? = null,
    val favoriteIds: Set<String> = emptySet(),
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var lastCenterLat = Constants.DEFAULT_LAT
    private var lastCenterLng = Constants.DEFAULT_LNG
    private var lastZoom = Constants.DEFAULT_ZOOM
    private var places: List<Place> = emptyList()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState.update { it.copy(isSeeding = false) }
            reload()
            if (repository.refreshFromRemoteIfStale(System.currentTimeMillis())) reload()
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    /** 지도 카메라가 멈출 때마다 호출: 보이는 영역의 장소를 조회하고 클러스터링 */
    fun onCameraIdle(centerLat: Double, centerLng: Double, zoom: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastZoom = zoom
        reload()
    }

    fun selectCategory(category: PlaceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        reload()
    }

    private fun reload() {
        if (_uiState.value.isSeeding) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            places = repository.getPlacesInBounds(
                centerLat = lastCenterLat,
                centerLng = lastCenterLng,
                radiusKm = radiusForZoom(lastZoom),
                category = _uiState.value.selectedCategory,
                limit = 500,
            )
            val clusters = clusterPlaces(places, lastZoom)
            _uiState.update { it.copy(isLoading = false, clusters = clusters) }
        }
    }
}
