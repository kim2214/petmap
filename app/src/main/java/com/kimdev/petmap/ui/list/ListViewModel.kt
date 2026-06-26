package com.kimdev.petmap.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.data.repository.withFavorites
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.OpeningHours
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    private var favoriteIds: Set<String> = emptySet()
    private var userLocation: UserLocation? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            userLocation = locationProvider.lastLocation()
            _uiState.update { it.copy(hasLocation = userLocation != null) }
            // 검색어/카테고리/정렬/필터 변경을 디바운스하여 재조회
            _uiState
                .map { SearchKey(it.query, it.selectedCategories, it.sortByDistance, it.openNowOnly) }
                .distinctUntilChanged()
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
        _uiState.update { it.copy(isLoading = true) }
        val loc = userLocation
        // 영업중 필터가 켜지면 걸러진 뒤에도 충분히 남도록 더 넉넉히 가져온다
        val fetchLimit = if (key.openNowOnly) 400 else 200

        var results = if (key.sortByDistance && loc != null) {
            repository.searchNearby(key.query, key.categories, loc.lat, loc.lng, fetchLimit)
        } else {
            repository.search(key.query, key.categories, fetchLimit)
        }

        if (key.openNowOnly) {
            val now = LocalDateTime.now()
            results = results.filter {
                OpeningHours.isOpenNow(it.operatingTime, it.closedDays, now) == true
            }
        }

        _uiState.update {
            it.copy(isLoading = false, places = results.take(200).withFavorites(favoriteIds))
        }
    }

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

    fun setOpenNowOnly(enabled: Boolean) =
        _uiState.update { it.copy(openNowOnly = enabled) }

    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }
}
