package com.kimdev.petmap.ui.list

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.DefaultDispatcher
import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.data.repository.withFavorites
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.OpeningHours
import com.kimdev.petmap.domain.util.distanceMeters
import com.kimdev.petmap.ui.common.SavedFilters
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.time.LocalDateTime
import javax.inject.Inject

data class ListUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val query: String = "",
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    val sortByDistance: Boolean = false,
    val openNowOnly: Boolean = false,
    val hasLocation: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    /** 조회 실패 여부. true 면 "결과 없음"이 아니라 에러 상태로 표시한다. */
    val isError: Boolean = false,
    /** 다음 페이지가 있을 수 있음 → 목록 끝에서 추가 로드 */
    val canLoadMore: Boolean = false,
    /** 추가 페이지 로딩 중(첫 로딩 스피너와 구분) */
    val isLoadingMore: Boolean = false,
    /** 상한까지 불러왔다 → 더 보려면 검색어·필터로 좁혀야 한다는 안내 */
    val reachedLimit: Boolean = false,
)

/**
 * 목록 화면의 일회성 이벤트. 상태에 담고 consume 하면 `LaunchedEffect(key)` 가 취소되어
 * 스낵바가 표시 도중 사라진다(실제로 발생했던 버그) → 이벤트로 분리한다.
 */
sealed interface ListEvent {
    /** 권한은 있지만 위치를 얻지 못함(위치 서비스 꺼짐 등) */
    data object LocationUnavailable : ListEvent
}

private data class SearchKey(
    val query: String,
    val categories: Set<PlaceCategory>,
    val sortByDistance: Boolean,
    val openNowOnly: Boolean,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val locationProvider: LocationProvider,
    private val recentSearchStore: RecentSearchStore,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // 프로세스 사망 후에도 검색어·필터·정렬을 복원한다.
    private val _uiState = MutableStateFlow(
        ListUiState(
            query = savedStateHandle[KEY_QUERY] ?: "",
            selectedCategories = SavedFilters.namesToCategories(savedStateHandle[KEY_CATEGORIES]),
            sortByDistance = savedStateHandle[KEY_SORT_BY_DISTANCE] ?: false,
            openNowOnly = savedStateHandle[KEY_OPEN_NOW_ONLY] ?: false,
        )
    )
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    private var favoriteIds: Set<String> = emptySet()
    private var userLocation: UserLocation? = null
    // 현재까지 불러온 페이지 크기. 검색 조건이 바뀌면 첫 페이지로 되돌린다.
    private var pageLimit = PAGE_SIZE
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            userLocation = locationProvider.lastLocation()
            // 콜드스타트 직후 lastLocation 은 흔히 null → 단발성 현재 위치로 보완한다.
            // (검색 플로우는 아래 별도 launch 에서 돌아가므로 이 지연이 목록 로딩을 막지 않는다)
            if (userLocation == null) userLocation = locationProvider.currentLocation()
            _uiState.update { it.copy(hasLocation = userLocation != null) }
            // 위치를 뒤늦게 확보했고 거리순 정렬이 켜져 있으면 거리 반영을 위해 재조회
            if (userLocation != null && _uiState.value.sortByDistance) refreshSearch()
        }
        // 검색어/카테고리/정렬/필터 변경을 디바운스하여 재조회 + 상태 저장
        viewModelScope.launch {
            _uiState
                .map { SearchKey(it.query, it.selectedCategories, it.sortByDistance, it.openNowOnly) }
                .distinctUntilChanged()
                .onEach { persist(it) }
                .debounce(250)
                .collect { key ->
                    // 조건이 바뀌면 페이지를 처음부터 다시 센다
                    pageLimit = PAGE_SIZE
                    loadMoreJob?.cancel()
                    runSearch(key)
                }
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                favoriteIds = ids
                _uiState.update { it.copy(places = it.places.withFavorites(ids)) }
            }
        }
        viewModelScope.launch {
            recentSearchStore.recent.collect { list ->
                _uiState.update { it.copy(recentSearches = list) }
            }
        }
    }

    private suspend fun runSearch(key: SearchKey, loadingMore: Boolean = false) {
        _uiState.update {
            if (loadingMore) it.copy(isLoadingMore = true, isError = false)
            else it.copy(isLoading = true, isError = false)
        }
        val loc = userLocation
        // 영업중 필터가 켜지면 걸러진 뒤에도 페이지가 채워지도록 더 넉넉히 가져온다
        val fetchLimit = if (key.openNowOnly) pageLimit * 2 else pageLimit

        runCatching {
            val fetched = if (key.sortByDistance && loc != null) {
                repository.searchNearby(key.query, key.categories, loc.lat, loc.lng, fetchLimit)
            } else {
                val results = repository.search(key.query, key.categories, fetchLimit)
                // 위치를 알면 거리순 정렬을 켜지 않아도 카드에 거리를 표시한다
                if (loc == null) results
                else results.map { it.copy(distanceMeters = distanceMeters(loc.lat, loc.lng, it.lat, it.lng)) }
            }
            val visible = if (key.openNowOnly) {
                val now = LocalDateTime.now()
                // 운영시간 파싱(정규식)은 CPU 작업이라 백그라운드에서 처리
                withContext(defaultDispatcher) {
                    fetched.filter { OpeningHours.isOpenNow(it.operatingTime, it.closedDays, now) == true }
                }
            } else {
                fetched
            }
            // 한도를 꽉 채워 돌아왔다면 DB 에 더 남아 있을 수 있다(필터 후 개수가 아니라 조회 개수로 판단)
            FetchResult(visible.take(pageLimit), hasMore = fetched.size >= fetchLimit)
        }.onSuccess { result ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    places = result.places.withFavorites(favoriteIds),
                    canLoadMore = result.hasMore && pageLimit < MAX_LIMIT,
                    reachedLimit = result.hasMore && pageLimit >= MAX_LIMIT,
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "search failed: ${e.message}")
            _uiState.update { it.copy(isLoading = false, isLoadingMore = false, isError = true) }
        }
    }

    /**
     * 목록 끝에 도달했을 때 다음 페이지를 이어 붙인다.
     * 로컬 DB 라 한도를 늘려 재조회하는 방식으로 충분하다(거리 정렬·영업중 필터가 조회 후
     * 후처리라 오프셋 기반 페이징과 맞지 않는다).
     */
    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || s.isLoadingMore || !s.canLoadMore) return
        pageLimit = (pageLimit + PAGE_SIZE).coerceAtMost(MAX_LIMIT)
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            runSearch(
                SearchKey(s.query, s.selectedCategories, s.sortByDistance, s.openNowOnly),
                loadingMore = true,
            )
        }
    }

    private data class FetchResult(val places: List<Place>, val hasMore: Boolean)

    /** 현재 검색 조건으로 즉시 재조회(위치 뒤늦은 확보·에러 후 재시도). */
    private fun refreshSearch() {
        val s = _uiState.value
        viewModelScope.launch {
            runSearch(SearchKey(s.query, s.selectedCategories, s.sortByDistance, s.openNowOnly))
        }
    }

    /** 에러 상태에서 사용자가 "다시 시도" 를 눌렀을 때. */
    fun retry() = refreshSearch()

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleCategory(category: PlaceCategory) = _uiState.update {
        val next = if (category in it.selectedCategories) it.selectedCategories - category
        else it.selectedCategories + category
        it.copy(selectedCategories = next)
    }

    fun clearCategories() = _uiState.update { it.copy(selectedCategories = emptySet()) }

    fun recordRecentSearch(query: String) = recentSearchStore.add(query)

    fun removeRecentSearch(query: String) = recentSearchStore.remove(query)

    fun clearRecentSearches() = recentSearchStore.clear()

    fun setSortByDistance(enabled: Boolean) =
        _uiState.update { it.copy(sortByDistance = enabled) }

    /**
     * "거리순" 칩을 눌렀는데 위치가 아직 없을 때(권한 방금 허용·위치 미확보) 호출.
     * 위치를 확보하면 거리순 정렬을 켜고, 실패하면 스낵바 안내 플래그를 올린다.
     */
    fun enableDistanceSortWithLocation() {
        viewModelScope.launch {
            if (userLocation == null) {
                userLocation = locationProvider.lastLocation() ?: locationProvider.currentLocation()
            }
            val has = userLocation != null
            _uiState.update { it.copy(hasLocation = has) }
            when {
                !has -> _events.send(ListEvent.LocationUnavailable)
                // SearchKey 변경으로 자동 재조회된다
                !_uiState.value.sortByDistance -> setSortByDistance(true)
                // 정렬은 이미 켜져 있었지만 위치가 이제 생김 → 거리 반영 재조회
                else -> refreshSearch()
            }
        }
    }

    fun setOpenNowOnly(enabled: Boolean) =
        _uiState.update { it.copy(openNowOnly = enabled) }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    /** 검색어·필터·정렬을 SavedStateHandle 에 저장(프로세스 사망 대비). */
    private fun persist(key: SearchKey) {
        savedStateHandle[KEY_QUERY] = key.query
        savedStateHandle[KEY_CATEGORIES] = SavedFilters.categoriesToNames(key.categories)
        savedStateHandle[KEY_SORT_BY_DISTANCE] = key.sortByDistance
        savedStateHandle[KEY_OPEN_NOW_ONLY] = key.openNowOnly
    }

    companion object {
        private const val TAG = "ListViewModel"

        /** 한 페이지 크기와 총 상한. 상한에 닿으면 검색어·필터로 좁히도록 안내한다. */
        private const val PAGE_SIZE = 200
        private const val MAX_LIMIT = 2000

        private const val KEY_QUERY = "list_query"
        private const val KEY_CATEGORIES = "list_categories"
        private const val KEY_SORT_BY_DISTANCE = "list_sort_by_distance"
        private const val KEY_OPEN_NOW_ONLY = "list_open_now_only"
    }
}
