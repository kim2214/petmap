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
import com.kimdev.petmap.ui.common.SavedFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
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
    /** 권한은 있지만 위치를 얻지 못함(위치 꺼짐 등) → 스낵바 안내 (화면이 소비) */
    val locationUnavailable: Boolean = false,
)

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

    private var favoriteIds: Set<String> = emptySet()
    private var userLocation: UserLocation? = null

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
                .collect { runSearch(it) }
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

    private suspend fun runSearch(key: SearchKey) {
        _uiState.update { it.copy(isLoading = true, isError = false) }
        val loc = userLocation
        // 영업중 필터가 켜지면 걸러진 뒤에도 충분히 남도록 더 넉넉히 가져온다
        val fetchLimit = if (key.openNowOnly) 400 else 200

        runCatching {
            val fetched = if (key.sortByDistance && loc != null) {
                repository.searchNearby(key.query, key.categories, loc.lat, loc.lng, fetchLimit)
            } else {
                repository.search(key.query, key.categories, fetchLimit)
            }
            if (key.openNowOnly) {
                val now = LocalDateTime.now()
                // 운영시간 파싱(정규식) × 최대 400건은 CPU 작업이라 백그라운드에서 처리
                withContext(defaultDispatcher) {
                    fetched.filter { OpeningHours.isOpenNow(it.operatingTime, it.closedDays, now) == true }
                }
            } else {
                fetched
            }
        }.onSuccess { results ->
            _uiState.update {
                it.copy(isLoading = false, places = results.take(200).withFavorites(favoriteIds))
            }
        }.onFailure { e ->
            Log.w(TAG, "search failed: ${e.message}")
            _uiState.update { it.copy(isLoading = false, isError = true) }
        }
    }

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
            _uiState.update { it.copy(hasLocation = has, locationUnavailable = !has) }
            when {
                !has -> Unit
                // SearchKey 변경으로 자동 재조회된다
                !_uiState.value.sortByDistance -> setSortByDistance(true)
                // 정렬은 이미 켜져 있었지만 위치가 이제 생김 → 거리 반영 재조회
                else -> refreshSearch()
            }
        }
    }

    /** 위치 확보 실패 안내를 화면이 표시한 뒤 호출 */
    fun consumeLocationUnavailable() =
        _uiState.update { it.copy(locationUnavailable = false) }

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
        private const val KEY_QUERY = "list_query"
        private const val KEY_CATEGORIES = "list_categories"
        private const val KEY_SORT_BY_DISTANCE = "list_sort_by_distance"
        private const val KEY_OPEN_NOW_ONLY = "list_open_now_only"
    }
}
