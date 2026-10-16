package com.kimdev.petmap.ui.map

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.DefaultDispatcher
import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.data.local.PetProfileStore
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.PetFilter
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.GeoBounds
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.model.Region
import com.kimdev.petmap.domain.model.RegionCount
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.ui.common.SavedFilters
import com.kimdev.petmap.ui.navigation.Routes
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MapUiState(
    val isSeeding: Boolean = true,
    /** 에셋 DB 준비(복사/open) 실패 → 전면 오류 안내 + 재시도. 조회는 성공할 때까지 막는다 */
    val seedFailed: Boolean = false,
    val isLoading: Boolean = false,
    val clusters: List<MapCluster> = emptyList(),
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    /** 반려동물 동반 조건 (모든 크기/실내/실외). 여러 개면 AND, SQL 단계에서 걸린다 */
    val petFilters: Set<PetFilter> = emptySet(),
    /** 설정에 등록한 내 반려견 체중(kg). null 이면 '내 아이 가능' 칩이 등록 다이얼로그를 먼저 띄운다 */
    val myPetKg: Double? = null,
    /** 지역 선택 시트용 개수. 시트를 처음 열 때 채워진다(비어 있으면 로딩 중) */
    val regionCounts: List<RegionCount> = emptyList(),
    /** 지역 개수 조회 실패 — 시트가 무한 로딩 대신 재시도를 보인다 */
    val regionCountsError: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    /** 지도를 충분히 이동/축소해 현재 표시 데이터가 오래된 상태 → "이 지역에서 다시 검색" 노출 */
    val canResearch: Boolean = false,
    /**
     * 검색 확정(키보드 검색 키) 상태: 뷰포트 조회 대신 검색 결과 전체를 마커로 보여준다.
     * 카메라를 움직여도 재조회하지 않고 로컬 재클러스터링만 한다. 검색어를 지우면 해제.
     */
    val searchMarkers: SearchMarkers? = null,
)

/** 지도에 뿌린 검색 결과 요약 (칩 아래 안내 pill) */
data class SearchMarkers(val query: String, val count: Int, val truncated: Boolean)

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
    /** 검색 결과 전체가 보이도록 카메라를 결과 범위에 맞춤 */
    data class FitPlaces(val places: List<Place>) : MapEvent
    /** 지역 선택: 그 지역 장소 범위로 카메라를 맞추고, 도착 후 재조회한다 */
    data class FitRegion(val region: Region, val bounds: GeoBounds) : MapEvent
    /** 지역 선택했지만 장소가 없음 */
    data class RegionEmpty(val region: Region) : MapEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val recentSearchStore: RecentSearchStore,
    private val petProfileStore: PetProfileStore,
    private val locationProvider: LocationProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // 프로세스 사망 후에도 카테고리·동반 조건 필터를 복원한다.
    private val _uiState = MutableStateFlow(
        MapUiState(
            selectedCategories = SavedFilters.namesToCategories(savedStateHandle[KEY_CATEGORIES]),
            petFilters = SavedFilters.namesToPetFilters(savedStateHandle[KEY_PET_FILTERS]),
            myPetKg = petProfileStore.weightKg.value,
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // 화면이 없는 동안(탭 전환 등) 발생한 이벤트는 버퍼에서 대기하다 재진입 시 전달된다.
    private val _events = Channel<MapEvent>(Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    // 미리보기 시트의 거리 표시용 사용자 위치. 없으면(권한 거부 등) 거리만 생략된다.
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    // 바텀시트 대상(마커 미리보기 / 겹친 클러스터 목록). 화면의 remember 에 두면 회전·글자 크기 변경으로
    // Activity 가 재생성될 때 시트가 사라지고, 포커스 지도의 미리보기는 이벤트가 다시 오지 않아 복구되지 않는다.
    private val _previewPlace = MutableStateFlow<Place?>(null)
    val previewPlace: StateFlow<Place?> = _previewPlace.asStateFlow()

    private val _clusterList = MutableStateFlow<List<Place>?>(null)
    val clusterList: StateFlow<List<Place>?> = _clusterList.asStateFlow()

    /** 미리보기 시트 열기(null 이면 닫기) */
    fun showPreview(place: Place?) {
        _previewPlace.value = place
    }

    /** 클러스터 목록 시트 열기(null 이면 닫기) */
    fun showClusterList(places: List<Place>?) {
        _clusterList.value = places
    }

    /**
     * 원격 갱신 후 열려 있는 시트의 장소를 새 데이터로 바꾼다. 데이터에서 빠진 장소는 시트에서 뺀다
     * (미리보기는 닫고, 클러스터 목록은 남은 장소만 — 하나도 없으면 닫는다).
     * 조회 중에 사용자가 시트를 닫거나 다른 장소를 열었으면 덮어쓰지 않는다.
     */
    private suspend fun refreshOpenSheets() {
        _previewPlace.value?.let { open ->
            val fresh = runCatching { repository.getPlaces(listOf(open.id)) }.getOrNull() ?: return@let
            _previewPlace.compareAndSet(open, fresh.firstOrNull())
        }
        _clusterList.value?.let { open ->
            val fresh = runCatching { repository.getPlaces(open.map { it.id }) }.getOrNull() ?: return@let
            _clusterList.compareAndSet(open, fresh.ifEmpty { null })
        }
    }

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
    // 검색 확정(마커 모드 진입/갱신) 잡. fetchJob 과 별도로 들어야 clearSearch 가 "진행 중인 검색"만
    // 정확히 취소할 수 있다 — 취소하지 않으면 ✕ 를 누른 뒤에 결과가 도착해 빈 검색창 + 마커 모드가 된다.
    private var searchJob: Job? = null
    // 첫 진입 현재 위치 이동을 이미 처리했는지(중복 이동 방지)
    private var initialLocationHandled = false
    // "지도에서 보기"로 push 된 지도인가(라우트 인자). 있으면 초기 현재 위치 이동을 양보하고
    // 첫 조회를 그 장소 주변으로 한다. 프로세스 사망 복원 시에도 같은 장소를 다시 비춘다.
    private val focusPlaceId: String? = savedStateHandle[Routes.MAP_ARG_FOCUS]
    private val focusRequested: Boolean get() = focusPlaceId != null
    private var initialFocusHandled = false

    /**
     * 이 지도가 특정 장소를 비추기 위해 push 된 것인지. 화면은 이 경우 첫 진입 Follow(내 위치 추적)를
     * 켜지 않는다 — 켜면 위치 소스가 카메라를 내 위치로 끌고 가 포커스 이동을 덮어쓴다.
     */
    val hasFocusTarget: Boolean get() = focusRequested

    init {
        seed()
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
                    // 확정된 검색어(마커로 표시 중)와 같으면 자동완성 목록을 다시 띄우지 않는다.
                    val results = if (q.isBlank() || q.trim() == _uiState.value.searchMarkers?.query) emptyList()
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
        // 필터 변경 시 저장(프로세스 사망 대비)
        viewModelScope.launch {
            _uiState.map { it.selectedCategories }
                .distinctUntilChanged()
                .onEach { savedStateHandle[KEY_CATEGORIES] = SavedFilters.categoriesToNames(it) }
                .collect {}
        }
        viewModelScope.launch {
            _uiState.map { it.petFilters }
                .distinctUntilChanged()
                .onEach { savedStateHandle[KEY_PET_FILTERS] = SavedFilters.petFiltersToNames(it) }
                .collect {}
        }
        // 원격 갱신으로 데이터가 바뀌면 지금 보이는 마커(검색 마커 모드면 같은 검색어)와 지역 개수를 다시 받는다
        viewModelScope.launch {
            repository.dataVersion.drop(1).collect {
                fetch()
                reloadRegionCounts()
                refreshOpenSheets()
            }
        }
        // 설정에서 체중이 바뀌면(등록 해제 포함) '내 아이' 필터가 켜진 경우 재조회한다
        viewModelScope.launch {
            petProfileStore.weightKg.drop(1).collect { kg ->
                val wasOn = PetFilter.MY_PET in _uiState.value.petFilters
                _uiState.update {
                    it.copy(myPetKg = kg, petFilters = if (kg == null) it.petFilters - PetFilter.MY_PET else it.petFilters)
                }
                if (wasOn) fetch()
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

    fun onSearchQueryChange(q: String) {
        // 백스페이스로 검색어를 다 지우는 것도 ✕ 와 같은 해제다 — 마커 모드(또는 진행 중인 검색 확정)를
        // 남기면 빈 검색창 + pill 만 남고 "다시 검색"·뒤로가기 어느 쪽으로도 빠져나올 수 없다.
        if (q.isBlank() && (_uiState.value.searchMarkers != null || searchJob?.isActive == true)) {
            clearSearch()
            return
        }
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun clearSearch() {
        val hadMarkers = _uiState.value.searchMarkers != null
        val hadPendingSearch = searchJob?.isActive == true
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchMarkers = null,
                // 진행 중이던 검색 확정을 취소했으면 그 잡이 켠 로딩도 함께 내린다
                isLoading = if (hadPendingSearch && !hadMarkers) false else it.isLoading,
            )
        }
        // 검색 마커를 내리면 원래의 뷰포트 조회로 복귀한다
        if (hadMarkers) fetch()
    }

    /**
     * 검색 확정: 검색어에 맞는 장소 전체(상한 [SEARCH_MARKERS_LIMIT])를 마커로 뿌린다. 자동완성(상위
     * 8건 중 하나 선택)과 달리 "근처 동물병원 전부"처럼 결과를 한눈에 비교할 때 쓴다.
     * 카테고리·동반 조건 필터는 SQL 단계에서 함께 걸린다(상한 전에 걸려야 결과가 유실되지 않는다).
     *
     * @param fitCamera 결과 범위로 카메라를 맞출지. 키보드 검색 키에서는 true, 마커 모드 중 필터
     *   토글·재시도로 같은 검색어를 다시 뿌릴 때는 false — 사용자가 줌인해 보던 곳에서 전국 뷰로 튀지 않게.
     */
    fun submitSearch(rawQuery: String, fitCamera: Boolean = true) {
        val query = rawQuery.trim()
        if (query.isBlank()) return
        fetchJob?.cancel()
        searchJob?.cancel()
        clusterJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchResults = emptyList()) }
            runCatching {
                repository.search(
                    query,
                    _uiState.value.selectedCategories,
                    _uiState.value.petFilters,
                    myPetKg = _uiState.value.myPetKg,
                    limit = SEARCH_MARKERS_LIMIT + 1,
                )
            }.onSuccess { found ->
                // 0건이면 마커 모드에 들어가지 않는다 — 기존 마커를 지우고 "0곳 표시 중" 을 띄우는 대신
                // 자동완성의 "검색 결과가 없어요" 안내 + NoResults 스낵바만으로 충분하다
                if (found.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(MapEvent.NoResults)
                    return@onSuccess
                }
                val truncated = found.size > SEARCH_MARKERS_LIMIT
                val shown = found.take(SEARCH_MARKERS_LIMIT)
                places = shown
                val clusters = withContext(defaultDispatcher) { clusterPlaces(shown, lastZoom) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        clusters = clusters,
                        canResearch = false,
                        searchMarkers = SearchMarkers(query, shown.size, truncated),
                    )
                }
                if (fitCamera) _events.send(MapEvent.FitPlaces(shown))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "search markers failed: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
                _events.send(MapEvent.LoadFailed)
            }
        }
    }

    /** 검색 확정(결과 선택 등) 시 최근 검색어로 저장 */
    fun recordRecentSearch(query: String) = recentSearchStore.add(query)

    fun removeRecentSearch(query: String) = recentSearchStore.remove(query)

    fun clearRecentSearches() = recentSearchStore.clear()

    /**
     * 카메라가 이동/줌될 때마다 호출.
     * - 이미 불러온 장소들을 새 줌에 맞게 **로컬에서만** 재클러스터링(데이터 재조회 없음).
     * - 마지막 조회 지점에서 충분히 멀어지거나 조회 반경이 달라지면 "다시 검색" 버튼을 노출.
     */
    /**
     * 에셋 DB 준비. 실패하면 [MapUiState.seedFailed] 로 전면 안내하고 조회를 시작하지 않는다
     * (실패 원인은 대개 저장 공간 부족·파일 손상이라 조회도 같은 이유로 실패한다).
     */
    private fun seed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSeeding = true, seedFailed = false) }
            try {
                repository.ensureSeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "에셋 DB 준비 실패", e)
                _uiState.update { it.copy(isSeeding = false, seedFailed = true) }
                return@launch
            }
            _uiState.update { it.copy(isSeeding = false) }
            applyInitialFocus()
            fetch()
        }
    }

    /**
     * 라우트 인자로 받은 포커스 장소가 있으면 첫 조회 위치를 그 장소로 잡고 카메라 이동 이벤트를 보낸다.
     * 조회는 뒤이은 [fetch] 한 번으로 끝난다(화면은 이 이벤트에서 재조회하지 않는다).
     */
    private suspend fun applyInitialFocus() {
        val id = focusPlaceId ?: return
        if (initialFocusHandled) return
        initialFocusHandled = true
        val place = runCatching { repository.getPlace(id) }.getOrNull() ?: return
        lastCenterLat = place.lat
        lastCenterLng = place.lng
        lastZoom = FOCUS_ZOOM
        _events.send(MapEvent.FocusPlace(place))
    }

    /** 준비 실패 안내의 "다시 시도" — 공간 확보 후 재시도하면 Room 이 에셋 복사를 다시 시도한다 */
    fun retrySeeding() {
        if (_uiState.value.seedFailed) seed()
    }

    fun onCameraMove(centerLat: Double, centerLng: Double, zoom: Double) {
        lastCenterLat = centerLat
        lastCenterLng = centerLng
        lastZoom = zoom
        if (_uiState.value.isSeeding || _uiState.value.seedFailed) return

        // 줌 버킷이 바뀌면 조회 반경과 집계 격자 크기가 달라져, 지금 표시 중인 개수 자체가 의미를
        // 잃는다(예: 12km 격자로 센 개수를 4km 뷰에 그대로 남기면 오정보). 로컬 DB 쿼리라 비용이
        // 낮으므로 이 경우만 자동 재조회하고, 같은 줌에서의 팬 이동은 "다시 검색" 버튼을 유지한다.
        if (_uiState.value.searchMarkers == null && radiusForZoom(zoom) != radiusForZoom(loadedZoom)) {
            fetch()
            return
        }

        // 검색 마커 모드: 결과 집합이 고정이라 "다시 검색"은 의미가 없다 — 재클러스터링만 한다
        val stale = _uiState.value.searchMarkers == null && isResearchNeeded(
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

    private var regionCountsJob: Job? = null

    /**
     * 지역 선택 시트가 열려 있는 동안 호출 — 개수는 데이터가 바뀌기 전까지 그대로라 한 번만 가져온다.
     * 시트는 rememberSaveable 이라 프로세스 복원 후에도 다시 열리므로 화면이 열림 상태를 키로 호출한다.
     */
    fun loadRegionCounts() {
        if (_uiState.value.regionCounts.isNotEmpty() || regionCountsJob?.isActive == true) return
        _uiState.update { it.copy(regionCountsError = false) }
        regionCountsJob = viewModelScope.launch {
            runCatching { repository.getRegionCounts() }
                .onSuccess { counts -> _uiState.update { it.copy(regionCounts = counts) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "getRegionCounts failed: ${e.message}")
                    _uiState.update { it.copy(regionCountsError = true) }
                }
        }
    }

    /** 데이터 교체 후: 이미 불러 둔(또는 불러오는 중인) 지역 개수만 새로 받는다 */
    private fun reloadRegionCounts() {
        val wasLoaded = _uiState.value.regionCounts.isNotEmpty() || regionCountsJob?.isActive == true
        regionCountsJob?.cancel()
        _uiState.update { it.copy(regionCounts = emptyList()) }
        if (wasLoaded) loadRegionCounts()
    }

    /**
     * 지역 선택: 지도는 뷰포트 조회라 필터를 거는 대신 그 지역 장소 범위로 카메라를 옮긴다.
     * 검색 마커 모드였다면 해제한다(고정 결과 집합 위에서 지역 이동은 뜻이 없다).
     */
    fun selectRegion(region: Region) {
        viewModelScope.launch {
            val bounds = runCatching { repository.getRegionBounds(region) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "getRegionBounds failed: ${e.message}")
                }
                .getOrNull()
            if (bounds == null) {
                _events.send(MapEvent.RegionEmpty(region))
                return@launch
            }
            if (_uiState.value.searchMarkers != null) clearSearch()
            _events.send(MapEvent.FitRegion(region, bounds))
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

    fun togglePetFilter(filter: PetFilter) {
        _uiState.update {
            val next = if (filter in it.petFilters) it.petFilters - filter else it.petFilters + filter
            it.copy(petFilters = next)
        }
        fetch()
    }

    /** '내 아이 가능' 칩에서 체중을 등록/수정하면 저장과 함께 필터를 켠다 (설정 화면과 같은 저장소) */
    fun setMyPetWeight(kg: Double) {
        petProfileStore.setWeight(kg)
        _uiState.update { it.copy(myPetKg = kg, petFilters = it.petFilters + PetFilter.MY_PET) }
        fetch()
    }

    /** 현재 카메라 위치 기준으로 DB 조회 후 클러스터링. canResearch 해제. */
    private fun fetch() {
        if (_uiState.value.isSeeding || _uiState.value.seedFailed) return
        // 검색 마커 모드에서 필터가 바뀌면 같은 검색어로 결과를 다시 뿌린다 (카메라는 그대로)
        _uiState.value.searchMarkers?.let { submitSearch(it.query, fitCamera = false); return }
        // 재조회가 클러스터 결과를 확정하므로 진행 중인 카메라-이동 클러스터링은 취소.
        clusterJob?.cancel()
        // 진행 중인 이전 조회도 취소 → 필터 연타/줌 연속 변경 시 결과 역전 방지.
        // 아직 마커 모드에 들어가기 전인 검색 확정도 취소한다 — 늦게 끝나면 옛 필터로 마커 모드에 들어간다.
        fetchJob?.cancel()
        searchJob?.cancel()
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
                petFilters = _uiState.value.petFilters,
                myPetKg = _uiState.value.myPetKg,
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
                    petFilters = _uiState.value.petFilters,
                    myPetKg = _uiState.value.myPetKg,
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
                petFilters = _uiState.value.petFilters,
                myPetKg = _uiState.value.myPetKg,
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
        private const val KEY_PET_FILTERS = "map_pet_filters"

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

        /** "지도에서 보기"로 특정 장소를 비출 때의 줌. */
        const val FOCUS_ZOOM = 16.0

        /**
         * 검색 확정 시 마커로 뿌리는 결과 상한. 목록 화면 상한(200)과 맞춘다 — 그 이상은 클러스터로
         * 뭉쳐도 한눈에 비교할 수 없고, 넘치면 pill 에 "이상"으로 알려 검색어를 좁히게 한다.
         */
        const val SEARCH_MARKERS_LIMIT = 200
    }
}
