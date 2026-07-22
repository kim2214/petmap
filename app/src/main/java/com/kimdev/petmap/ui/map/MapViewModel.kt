package com.kimdev.petmap.ui.map

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.DefaultDispatcher
import com.kimdev.petmap.core.common.MapFocusBus
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.ui.common.SavedFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** 조회 결과가 0건 → 스낵바로 안내 (화면이 소비 후 consumeNoResults 호출) */
    val showNoResults: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val mapFocusBus: MapFocusBus,
    private val recentSearchStore: RecentSearchStore,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // 프로세스 사망 후에도 카테고리 필터를 복원한다.
    private val _uiState = MutableStateFlow(
        MapUiState(selectedCategories = SavedFilters.namesToCategories(savedStateHandle[KEY_CATEGORIES]))
    )
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
    // 카메라 이동 시 재클러스터링 잡. 연속 이동하면 이전 계산을 취소하고 최신 것만 반영.
    private var clusterJob: Job? = null

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
                    // 검색 실패로 collector 가 취소되면 자동완성이 영구 중단되므로 예외를 흡수한다.
                    val results = if (q.isBlank()) emptyList()
                    else runCatching { repository.search(q.trim(), limit = 8) }
                        .onFailure { Log.w(TAG, "search failed: ${it.message}") }
                        .getOrDefault(emptyList())
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
        // 최근 검색어 관찰
        viewModelScope.launch {
            recentSearchStore.recent.collect { list ->
                _uiState.update { it.copy(recentSearches = list) }
            }
        }
        // 카테고리 필터 변경 시 저장(프로세스 사망 대비)
        viewModelScope.launch {
            _uiState.map { it.selectedCategories }
                .distinctUntilChanged()
                .onEach { savedStateHandle[KEY_CATEGORIES] = SavedFilters.categoriesToNames(it) }
                .collect {}
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

    /** 0건 안내를 화면이 표시한 뒤 호출 */
    fun consumeNoResults() = _uiState.update { it.copy(showNoResults = false) }

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

        val stale = isResearchNeeded(
            loadedCenterLat, loadedCenterLng, loadedZoom,
            centerLat, centerLng, zoom,
        )
        // 저줌 집계 모드에선 개별 좌표를 갖고 있지 않아(places 비어 있음) 로컬 재클러스터링이 불가하다.
        // 이 경우(또는 결과 없음) 기존 클러스터를 유지하고 "다시 검색" 노출 여부만 갱신한다.
        if (places.isEmpty()) {
            _uiState.update { it.copy(canResearch = stale) }
            return
        }
        // 클러스터링(최대 수백 개 좌표 연산)은 백그라운드에서 수행해 지도 제스처 중 메인 스레드 부담을 줄인다.
        val snapshot = places
        clusterJob?.cancel()
        clusterJob = viewModelScope.launch {
            val clusters = withContext(defaultDispatcher) { clusterPlaces(snapshot, zoom) }
            _uiState.update { it.copy(clusters = clusters, canResearch = stale) }
        }
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
        // 재조회가 클러스터 결과를 확정하므로 진행 중인 카메라-이동 클러스터링은 취소.
        clusterJob?.cancel()
        loadedCenterLat = lastCenterLat
        loadedCenterLng = lastCenterLng
        loadedZoom = lastZoom
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (loadedZoom < AGGREGATE_ZOOM_BELOW) {
                fetchAggregated()
            } else {
                fetchExact()
            }
        }
    }

    /** 고줌(도시/거리) 경로: 뷰포트 내 개별 장소를 가져와 클라이언트에서 클러스터링. */
    private suspend fun fetchExact() {
        runCatching {
            repository.getPlacesInBounds(
                centerLat = loadedCenterLat,
                centerLng = loadedCenterLng,
                radiusKm = radiusForZoom(loadedZoom),
                categories = _uiState.value.selectedCategories,
                limit = 500,
            )
        }.onSuccess { loaded ->
            places = loaded
            val clusters = withContext(defaultDispatcher) { clusterPlaces(loaded, lastZoom) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    clusters = clusters,
                    canResearch = false,
                    showNoResults = loaded.isEmpty(),
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "getPlacesInBounds failed: ${e.message}")
            // 실패 시 "이 지역에서 다시 검색" 버튼을 다시 노출해 재시도 동선을 준다.
            _uiState.update { it.copy(isLoading = false, canResearch = true) }
        }
    }

    /**
     * 저줌(광역) 경로: SQL 그리드 집계로 셀별 개수만 가져온다.
     * 개별 로우 마샬링·클라이언트 클러스터링을 생략하고, 중심 근처 500개가 아니라
     * 화면 전역을 실제 개수 기반 클러스터로 고르게 덮는다.
     */
    private suspend fun fetchAggregated() {
        runCatching {
            repository.getClusterCells(
                centerLat = loadedCenterLat,
                centerLng = loadedCenterLng,
                radiusKm = radiusForZoom(loadedZoom),
                categories = _uiState.value.selectedCategories,
                gridDivisions = GRID_DIVISIONS,
                limit = 500,
            )
        }.onSuccess { cells ->
            // 개별 좌표를 보유하지 않으므로 로컬 재클러스터링 대상(places)은 비운다.
            places = emptyList()
            val clusters = cells.map { cell ->
                MapCluster(
                    id = "cell_${cell.lat}_${cell.lng}",
                    lat = cell.lat,
                    lng = cell.lng,
                    members = emptyList(),
                    aggregatedCount = cell.count,
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    clusters = clusters,
                    canResearch = false,
                    showNoResults = cells.isEmpty(),
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "getClusterCells failed: ${e.message}")
            _uiState.update { it.copy(isLoading = false, canResearch = true) }
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val KEY_CATEGORIES = "map_categories"

        // 이 줌 미만(광역, 반경 40·120km)에선 개별 로우 대신 SQL 그리드 집계로 개요를 그린다.
        private const val AGGREGATE_ZOOM_BELOW = 11.0
        // 뷰포트를 나눌 격자 분할 수(가로/세로). 집계 셀(=클러스터 점) 최대 개수 ≈ 분할²을 제한.
        private const val GRID_DIVISIONS = 24
    }
}
