package com.kimdev.petmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.MapFocusBus
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
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

data class MapUiState(
    val isSeeding: Boolean = true,
    val isLoading: Boolean = false,
    val clusters: List<MapCluster> = emptyList(),
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val focusTarget: Place? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val mapFocusBus: MapFocusBus,
    private val recentSearchStore: RecentSearchStore,
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
        // 검색어 변경 → 디바운스 후 자동완성 결과 조회
        viewModelScope.launch {
            _uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(250)
                .collect { q ->
                    val results = if (q.isBlank()) emptyList()
                    else repository.search(q.trim(), limit = 8)
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
        // 최근 검색어 관찰
        viewModelScope.launch {
            recentSearchStore.recent.collect { list ->
                _uiState.update { it.copy(recentSearches = list) }
            }
        }
        // "지도에서 보기" 요청 → 해당 장소를 포커스 대상으로
        viewModelScope.launch {
            mapFocusBus.targetPlaceId.collect { id ->
                if (id == null) {
                    _uiState.update { it.copy(focusTarget = null) }
                } else {
                    val place = repository.getPlace(id)
                    _uiState.update { it.copy(focusTarget = place) }
                }
            }
        }
    }

    /** 포커스 이동을 화면이 처리한 뒤 호출 (버스/상태 초기화) */
    fun consumeFocus() {
        mapFocusBus.consume()
    }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    fun onSearchQueryChange(q: String) = _uiState.update { it.copy(searchQuery = q) }

    fun clearSearch() = _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }

    /** 검색 확정(결과 선택 등) 시 최근 검색어로 저장 */
    fun recordRecentSearch(query: String) = recentSearchStore.add(query)

    fun removeRecentSearch(query: String) = recentSearchStore.remove(query)

    fun clearRecentSearches() = recentSearchStore.clear()

    /** 지도 카메라가 멈출 때마다 호출: 보이는 영역의 장소를 조회하고 클러스터링 */
    fun onCameraIdle(centerLat: Double, centerLng: Double, zoom: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastZoom = zoom
        reload()
    }

    fun toggleCategory(category: PlaceCategory) {
        _uiState.update {
            val next = if (category in it.selectedCategories) it.selectedCategories - category
            else it.selectedCategories + category
            it.copy(selectedCategories = next)
        }
        reload()
    }

    fun clearCategories() {
        _uiState.update { it.copy(selectedCategories = emptySet()) }
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
                categories = _uiState.value.selectedCategories,
                limit = 500,
            )
            val clusters = clusterPlaces(places, lastZoom)
            _uiState.update { it.copy(isLoading = false, clusters = clusters) }
        }
    }
}
