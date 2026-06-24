package com.kimdev.petmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.repository.withFavorites
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
    val places: List<Place> = emptyList(),
    val selectedCategory: PlaceCategory? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var favoriteIds: Set<String> = emptySet()
    private var lastCenterLat = com.kimdev.petmap.core.common.Constants.DEFAULT_LAT
    private var lastCenterLng = com.kimdev.petmap.core.common.Constants.DEFAULT_LNG
    private var lastRadiusKm = 12.0

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState.update { it.copy(isSeeding = false) }
            reload()
            // 하이브리드: 오래됐으면 백그라운드 갱신 후 재조회
            if (repository.refreshFromRemoteIfStale(nowMillis())) reload()
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                favoriteIds = ids
                _uiState.update { it.copy(places = it.places.withFavorites(ids)) }
            }
        }
    }

    /** 지도 카메라가 멈출 때마다 호출: 보이는 영역의 장소를 다시 조회 */
    fun onCameraIdle(centerLat: Double, centerLng: Double, radiusKm: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastRadiusKm = radiusKm
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
            val places = repository.getPlacesInBounds(
                centerLat = lastCenterLat,
                centerLng = lastCenterLng,
                radiusKm = lastRadiusKm,
                category = _uiState.value.selectedCategory,
            ).withFavorites(favoriteIds)
            _uiState.update { it.copy(isLoading = false, places = places) }
        }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
