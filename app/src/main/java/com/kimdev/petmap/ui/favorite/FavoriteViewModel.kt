package com.kimdev.petmap.ui.favorite

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

data class FavoriteUiState(
    /** 카테고리·검색어 필터가 적용된 목록 */
    val favorites: List<Place> = emptyList(),
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    val query: String = "",
    /** 필터와 무관한 전체 즐겨찾기 수. 0 이면 "아직 없음", >0 인데 favorites 가 비면 "필터 결과 없음" */
    val totalCount: Int = 0,
    /** 첫 emit 전(초기값)엔 true. 데이터가 오기 전 "즐겨찾기 없음" 이 잘못 깜빡이는 것을 막는다. */
    val isLoading: Boolean = true,
    val sort: FavoriteSort = FavoriteSort.RECENT,
)

/** 즐겨찾기 정렬. RECENT 는 DAO 순서(최근 추가순) 그대로 */
enum class FavoriteSort { RECENT, NAME, DISTANCE }

sealed interface FavoriteEvent {
    /** 가까운순을 골랐지만 위치를 얻지 못함(권한은 있음) */
    data object LocationUnavailable : FavoriteEvent
}

data class RecentUiState(
    /** 최근 본 순서(최신 우선). 하트 상태는 즐겨찾기와 동기화된다 */
    val places: List<Place> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val recentPlaceStore: RecentPlaceStore,
    private val locationProvider: LocationProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selectedCategories = MutableStateFlow<Set<PlaceCategory>>(emptySet())
    private val query = MutableStateFlow("")

    // 가까운순은 위치가 있어야 하므로 복원 시 RECENT 로 시작하고, 위치를 다시 얻으면 [selectSort] 가 켠다
    private val sort = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT)
            ?.let { runCatching { FavoriteSort.valueOf(it) }.getOrNull() }
            ?.takeIf { it != FavoriteSort.DISTANCE } ?: FavoriteSort.RECENT,
    )
    private val userLocation = MutableStateFlow<UserLocation?>(null)

    private val _events = Channel<FavoriteEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<FavoriteUiState> =
        combine(
            repository.observeFavorites(), selectedCategories, query, sort, userLocation,
        ) { favorites, selected, q, sortBy, loc ->
            // 로컬 목록(수십~수백 건)이라 DB 검색 없이 이름/주소 부분 일치로 충분하다
            val filtered = favorites
                .filter { selected.isEmpty() || it.category in selected }
                .filter {
                    q.isBlank() || it.name.contains(q, ignoreCase = true) ||
                        it.roadAddress.contains(q, ignoreCase = true)
                }
                .map { p -> loc?.let { p.copy(distanceMeters = distanceMeters(it.lat, it.lng, p.lat, p.lng)) } ?: p }
            val sorted = when (sortBy) {
                FavoriteSort.RECENT -> filtered
                FavoriteSort.NAME -> filtered.sortedWith(compareBy(NAME_ORDER) { it.name })
                FavoriteSort.DISTANCE -> filtered.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            }
            FavoriteUiState(
                favorites = sorted,
                selectedCategories = selected,
                query = q,
                totalCount = favorites.size,
                isLoading = false,
                sort = sortBy,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FavoriteUiState(),
        )

    /**
     * 최근 본 장소. ID 목록이나 데이터 세대(원격 갱신)가 바뀔 때만 DB 를 조회하고(최대 30건 IN 조회),
     * 하트는 즐겨찾기 ID 로 덧입힌다.
     * 조회 실패(에셋 DB 준비 실패 등)는 빈 목록으로 — 즐겨찾기 탭 전체를 에러로 만들 일은 아니다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentState: StateFlow<RecentUiState> =
        combine(
            combine(recentPlaceStore.recent, repository.dataVersion) { ids, _ -> ids }.mapLatest { ids ->
                try {
                    repository.getPlaces(ids)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "recent places load failed: ${e.message}")
                    emptyList()
                }
            },
            repository.observeFavoriteIds(),
        ) { places, favoriteIds ->
            RecentUiState(places = places.map { it.copy(isFavorite = it.id in favoriteIds) }, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecentUiState(),
        )

    /** 최근 본 장소에서 하트 — 즐겨찾기 목록과 달리 카드가 사라지지 않으므로 단순 토글 */
    fun toggleFavorite(place: Place) {
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    fun clearRecent() = recentPlaceStore.clear()

    /** 최근 본 장소 하나 지우기. 실행취소용으로 지우기 전 위치를 돌려준다(이미 없으면 null) */
    fun removeRecent(placeId: String): Int? {
        val index = recentPlaceStore.recent.value.indexOf(placeId).takeIf { it >= 0 } ?: return null
        recentPlaceStore.remove(placeId)
        return index
    }

    fun restoreRecent(placeId: String, index: Int) = recentPlaceStore.restore(placeId, index)

    /**
     * 정렬 변경. 가까운순은 위치를 먼저 얻는다(권한 요청은 화면 몫) — 못 얻으면 정렬을 바꾸지 않고 안내 이벤트.
     */
    fun selectSort(next: FavoriteSort) {
        if (next != FavoriteSort.DISTANCE) {
            setSort(next)
            return
        }
        viewModelScope.launch {
            val loc = locationProvider.lastLocation() ?: locationProvider.currentLocation()
            if (loc == null) {
                _events.send(FavoriteEvent.LocationUnavailable)
                return@launch
            }
            userLocation.value = loc
            setSort(FavoriteSort.DISTANCE)
        }
    }

    private fun setSort(next: FavoriteSort) {
        sort.value = next
        savedStateHandle[KEY_SORT] = next.name
    }

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun toggleCategory(category: PlaceCategory) = selectedCategories.update {
        if (category in it) it - category else it + category
    }

    fun clearCategories() {
        selectedCategories.value = emptySet()
    }

    /**
     * 즐겨찾기 삭제. 실행취소용 스냅샷을 돌려준다(이미 지워졌으면 null).
     * 화면 스코프가 아닌 viewModelScope 에서 실행해, 스낵바 도중 화면을 떠나도 삭제가 끊기지 않는다.
     */
    suspend fun removeFavorite(placeId: String): FavoriteSnapshot? =
        viewModelScope.async { repository.removeFavorite(placeId) }.await()

    /** 실행취소: 메모·추가 시각까지 삭제 직전 상태로 되살린다 */
    fun restoreFavorite(snapshot: FavoriteSnapshot) {
        viewModelScope.launch { repository.restoreFavorite(snapshot) }
    }

    private companion object {
        const val TAG = "FavoriteViewModel"
        const val KEY_SORT = "favorite_sort"

        /** 이름순: 앱 목록 정렬(DB sortKey)과 같게 한글 → 영문 → 숫자 → 기타, 같은 묶음 안에선 한국어 정렬 */
        val NAME_ORDER: Comparator<String> = run {
            val collator = Collator.getInstance(Locale.KOREAN)
            fun bucket(name: String): Int {
                val c = name.firstOrNull { it.isLetterOrDigit() } ?: return 3
                return when {
                    c in '\uAC00'..'\uD7A3' || c in '\u3131'..'\u318E' -> 0
                    c in 'a'..'z' || c in 'A'..'Z' -> 1
                    c.isDigit() -> 2
                    else -> 3
                }
            }
            compareBy<String> { bucket(it) }.then { a, b -> collator.compare(a, b) }
        }
    }
}
