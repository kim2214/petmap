package com.kimdev.petmap.ui.map

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.DefaultDispatcher
import com.kimdev.petmap.core.common.MapFocusBus
import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.ui.common.SavedFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** 지도를 충분히 이동/축소해 현재 표시 데이터가 오래된 상태 → "이 지역에서 다시 검색" 노출 */
    val canResearch: Boolean = false,
)

/**
 * 지도 화면의 일회성 이벤트.
 *
 * 상태(UiState)에 담고 화면이 consume 하는 방식은 위험하다. consume 이 상태를 되돌리면
 * `LaunchedEffect(state.flag)` 의 키가 바뀌어 실행 중인 코루틴이 취소되고, 스낵바처럼
 * 완료까지 suspend 하는 작업이 조용히 사라진다(실제로 발생했던 버그).
 */
sealed interface MapEvent {
    /** "지도에서 보기"로 지정된 장소로 카메라 이동 + 미리보기 */
    data class FocusPlace(val place: Place) : MapEvent
    /** 지도 첫 진입 시 현재 위치로 카메라 1회 이동 */
    data class MoveToLocation(val location: UserLocation) : MapEvent
    /** 조회 결과 0건 안내 */
    data object NoResults : MapEvent
    /** 조회 실패 안내 (재시도 액션 동반) — 실패를 숨기면 빈 지도가 "데이터 없음"으로 오해된다 */
    data object LoadFailed : MapEvent
    /** 저줌 집계 클러스터를 셀 범위 재조회로 펼친 결과 → 장소 목록 시트 표시 */
    data class ExpandCluster(val places: List<Place>) : MapEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val mapFocusBus: MapFocusBus,
    private val recentSearchStore: RecentSearchStore,
    private val locationProvider: LocationProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // 프로세스 사망 후에도 카테고리 필터를 복원한다.
    private val _uiState = MutableStateFlow(
        MapUiState(selectedCategories = SavedFilters.namesToCategories(savedStateHandle[KEY_CATEGORIES]))
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // 화면이 없는 동안(탭 전환 등) 발생한 이벤트는 버퍼에서 대기하다 재진입 시 전달된다.
    private val _events = Channel<MapEvent>(Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    // 미리보기 시트의 거리 표시용 사용자 위치. 없으면(권한 거부 등) 거리만 생략된다.
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

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
    // 데이터 조회 잡. 취소하지 않으면 먼저 시작한 조회가 늦게 끝나 오래된 결과가 최신을 덮어쓴다.
    private var fetchJob: Job? = null
    // 첫 진입 현재 위치 이동을 이미 처리했는지(중복 이동 방지)
    private var initialLocationHandled = false
    // "지도에서 보기" 요청을 받았는지. 요청이 있으면 초기 현재 위치 이동을 양보한다.
    // (DB 조회 전에 세팅하므로, 조회 지연 때문에 두 카메라 이동이 경쟁하지 않는다)
    private var focusRequested = false

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState.update { it.copy(isSeeding = false) }
            fetch()
        }
        // 거리 표시용 마지막 위치(권한 없으면 null). 카메라 이동과 무관하게 조용히 확보한다.
        viewModelScope.launch {
            _userLocation.value = locationProvider.lastLocation()
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
        // "지도에서 보기" 요청 → 해당 장소를 포커스 이벤트로
        viewModelScope.launch {
            mapFocusBus.requests.collect { id ->
                focusRequested = true
                val place = repository.getPlace(id)
                if (place != null) _events.send(MapEvent.FocusPlace(place))
            }
        }
    }

    /**
     * 지도 첫 진입 시 위치 권한이 허용된 경우 현재 위치로 카메라를 1회 이동한다.
     * 권한 확인은 호출 측(UI)이 하고, 여기선 위치를 얻지 못하면(null) 아무 것도 하지 않는다.
     * "지도에서 보기"로 특정 장소를 포커스 중이면 그쪽을 우선하고 초기 이동은 건너뛴다.
     */
    fun moveToCurrentLocationOnce() {
        if (initialLocationHandled || focusRequested) return
        viewModelScope.launch {
            val loc = locationProvider.lastLocation() ?: locationProvider.currentLocation() ?: return@launch
            _userLocation.value = loc
            // 위치 조회 중에 포커스 요청이 들어왔다면 카메라 이동을 양보한다.
            if (focusRequested) return@launch
            // 위치를 실제로 얻었을 때만 처리 완료로 표시 → 첫 시도에서 못 얻어도 다음 기회에 재시도.
            initialLocationHandled = true
            _events.send(MapEvent.MoveToLocation(loc))
        }
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

        // 줌 버킷이 바뀌면 조회 반경과 집계 격자 크기가 달라져, 지금 표시 중인 개수 자체가 의미를
        // 잃는다(예: 12km 격자로 센 개수를 4km 뷰에 그대로 남기면 오정보). 로컬 DB 쿼리라 비용이
        // 낮으므로 이 경우만 자동 재조회하고, 같은 줌에서의 팬 이동은 "다시 검색" 버튼을 유지한다.
        if (radiusForZoom(zoom) != radiusForZoom(loadedZoom)) {
            fetch()
            return
        }

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
        // 진행 중인 이전 조회도 취소 → 필터 연타/줌 연속 변경 시 결과 역전 방지.
        fetchJob?.cancel()
        loadedCenterLat = lastCenterLat
        loadedCenterLng = lastCenterLng
        loadedZoom = lastZoom
        fetchJob = viewModelScope.launch {
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
                limit = EXACT_LIMIT,
            )
        }.onSuccess { loaded ->
            // 한도에 걸렸다면 이 뷰포트가 개별 조회로 감당할 밀도를 넘었다는 뜻이다.
            // DAO 가 중심 거리순으로 자르므로 그대로 그리면 화면 가장자리가 텅 비고
            // 클러스터 개수도 실제보다 작게 표시된다 → 전역을 실제 개수로 덮는 집계 경로로 전환.
            if (loaded.size >= EXACT_LIMIT) {
                Log.i(TAG, "exact rows hit limit($EXACT_LIMIT) at zoom $loadedZoom → aggregate")
                fetchAggregated()
                return@onSuccess
            }
            places = loaded
            val clusters = withContext(defaultDispatcher) { clusterPlaces(loaded, lastZoom) }
            _uiState.update {
                it.copy(isLoading = false, clusters = clusters, canResearch = false)
            }
            if (loaded.isEmpty()) _events.send(MapEvent.NoResults)
        }.onFailure { e ->
            // 잡 취소(연속 카메라 이동·권한 허용 직후 재조회 등)는 실패가 아니다 —
            // runCatching 이 CancellationException 까지 잡으면 LoadFailed 가 오발된다.
            if (e is CancellationException) throw e
            Log.w(TAG, "getPlacesInBounds failed: ${e.message}")
            // 실패 시 "이 지역에서 다시 검색" 버튼을 다시 노출해 재시도 동선을 준다.
            _uiState.update { it.copy(isLoading = false, canResearch = true) }
            _events.send(MapEvent.LoadFailed)
        }
    }

    /**
     * 저줌(광역) 경로: SQL 그리드 집계로 셀별 개수만 가져온다.
     * 개별 로우 마샬링·클라이언트 클러스터링을 생략하고, 중심 근처 500개가 아니라
     * 화면 전역을 실제 개수 기반 클러스터로 고르게 덮는다.
     */
    /**
     * 저줌 집계 클러스터 탭: 셀 범위를 재조회해 실제 장소 목록으로 펼친다.
     * (집계 클러스터는 개별 좌표가 없어 지금까지는 줌인만 가능했다)
     */
    fun expandAggregatedCell(cell: GeoClusterCell) {
        viewModelScope.launch {
            runCatching {
                repository.getPlacesInCell(
                    cell = cell,
                    categories = _uiState.value.selectedCategories,
                    // 호출부에서 개수가 적을 때만 펼치지만, 집계 이후 데이터가 달라졌을 때를 대비한 상한
                    limit = AGGREGATE_EXPAND_MAX + 1,
                )
            }.onSuccess { found ->
                if (found.isNotEmpty()) _events.send(MapEvent.ExpandCluster(found))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "getPlacesInCell failed: ${e.message}")
                _events.send(MapEvent.LoadFailed)
            }
        }
    }

    private suspend fun fetchAggregated() {
        runCatching {
            repository.getClusterCells(
                centerLat = loadedCenterLat,
                centerLng = loadedCenterLng,
                radiusKm = radiusForZoom(loadedZoom),
                categories = _uiState.value.selectedCategories,
                gridDivisions = GRID_DIVISIONS,
                // 격자 수보다 작으면 어떤 셀이 버려지는지 정의되지 않아 지역이 통째로 비어 보인다.
                limit = GRID_DIVISIONS * GRID_DIVISIONS,
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
                    cell = cell,
                )
            }
            _uiState.update {
                it.copy(isLoading = false, clusters = clusters, canResearch = false)
            }
            if (cells.isEmpty()) _events.send(MapEvent.NoResults)
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "getClusterCells failed: ${e.message}")
            _uiState.update { it.copy(isLoading = false, canResearch = true) }
            _events.send(MapEvent.LoadFailed)
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val KEY_CATEGORIES = "map_categories"

        /**
         * 이 줌 미만에선 개별 로우 대신 SQL 그리드 집계로 개요를 그린다.
         *
         * 13 = 조회 반경 4km 경계. 실측(에셋 DB) 기준 서울·강남에서 반경 4km 는 약 620개인데
         * 반경 12km(줌 11~13)는 약 3,900개로 개별 조회 한도를 크게 넘는다. 넘는 구간을 개별
         * 조회로 처리하면 중심 거리순으로 잘려 화면 가장자리 마커가 사라지고 개수도 틀린다.
         */
        private const val AGGREGATE_ZOOM_BELOW = 13.0

        /**
         * 개별 로우 조회 상한. 실측 최대(반경 4km · 강남 약 620)의 3배 여유.
         * 클러스터링이 마커 수를 줄여주므로 병목은 마커가 아니라 로우 마샬링 비용이다.
         * 이 값에 걸리면 [fetchExact] 가 집계 경로로 폴백한다(데이터가 늘어나도 안전).
         */
        private const val EXACT_LIMIT = 2000

        /**
         * 뷰포트를 나눌 격자 분할 수(가로/세로). 집계 셀 = 클러스터 버블 하나.
         * 15 ≈ 1080px 화면을 [clusterPlaces] 와 같은 72px 셀로 나눈 값이라, 집계 경로와
         * 개별 경로의 클러스터 밀도가 시각적으로 비슷해진다. 더 키우면 버블끼리 겹친다.
         */
        private const val GRID_DIVISIONS = 15

        /**
         * 집계 클러스터를 탭했을 때 줌인 대신 목록으로 펼치는 개수 상한.
         * 이보다 많으면 목록이 과해 기존처럼 줌인한다. 시트 목록 높이(480dp)에 무리 없는 수준.
         */
        const val AGGREGATE_EXPAND_MAX = 30
    }
}
