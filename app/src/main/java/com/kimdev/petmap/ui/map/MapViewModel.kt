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
    /** 지도를 충분히 이동/축소해 현재 표시 데이터가 오래된 상태 → "이 지역에서 다시 검색" 노출 */
    val canResearch: Boolean = false,
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

    // 현재 카메라 위치
    private var lastCenterLat = Constants.DEFAULT_LAT
    private var lastCenterLng = Constants.DEFAULT_LNG
    private var lastZoom = Constants.DEFAULT_ZOOM
    // 데이터를 마지막으로 조회한 위치
    private var loadedCenterLat = Constants.DEFAULT_LAT
    private var loadedCenterLng = Constants.DEFAULT_LNG
    private var loadedZoom = Constants.DEFAULT_ZOOM
    private var places: List<Place> = emptyList()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState.update { it.copy(isSeeding = false) }
            fetch()
            if (repository.refreshFromRemoteIfStale(System.currentTimeMillis())) fetch()
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

    /**
     * 카메라가 이동/줌될 때마다 호출.
     * - 이미 불러온 장소들을 새 줌에 맞게 **로컬에서만** 재클러스터링(데이터 재조회 없음).
     * - 마지막 조회 지점에서 충분히 멀어지거나 조회 반경이 달라지면 "다시 검색" 버튼을 노출.
     */
    fun onCameraMove(centerLat: Double, centerLng: Double, zoom: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastZoom = zoom
        if (_uiState.value.isSeeding) return

        val clusters = clusterPlaces(places, zoom)
        val stale = isResearchNeeded(
            loadedCenterLat, loadedCenterLng, loadedZoom,
            centerLat, centerLng, zoom,
        )
        _uiState.update { it.copy(clusters = clusters, canResearch = stale) }
    }

    /** "이 지역에서 다시 검색" — 현재 카메라 위치 기준으로 데이터 재조회 */
    fun researchHere(centerLat: Double, centerLng: Double, zoom: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastZoom = zoom
        fetch()
    }

    fun toggleCategory(category: PlaceCategory) {
        _uiState.update {
            val next = if (category in it.selectedCategories) it.selectedCategories - category
            else it.selectedCategories + category
            it.copy(selectedCategories = next)
        }
        fetch()
    }

    fun clearCategories() {
        _uiState.update { it.copy(selectedCategories = emptySet()) }
        fetch()
    }

    /** 현재 카메라 위치 기준으로 DB 조회 후 클러스터링. canResearch 해제. */
    private fun fetch() {
        if (_uiState.value.isSeeding) return
        loadedCenterLat = lastCenterLat
        loadedCenterLng = lastCenterLng
        loadedZoom = lastZoom
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            places = repository.getPlacesInBounds(
                centerLat = loadedCenterLat,
                centerLng = loadedCenterLng,
                radiusKm = radiusForZoom(loadedZoom),
                categories = _uiState.value.selectedCategories,
                limit = 500,
            )
            val clusters = clusterPlaces(places, lastZoom)
            _uiState.update { it.copy(isLoading = false, clusters = clusters, canResearch = false) }
        }
    }

}
