package com.kimdev.petmap.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val recentPlaceStore: RecentPlaceStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[Routes.DETAIL_ARG_ID])

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 로드 실패 시에도 스피너가 멈추도록 예외를 흡수한다(화면은 place==null 을 "찾을 수 없음"으로 표시).
            val place = runCatching { repository.getPlace(placeId) }.getOrNull()
            _uiState.update { it.copy(isLoading = false, place = place) }
            if (place == null) return@launch
            // 실제로 열린 장소만 "최근 본"에 남긴다(없는 ID 딥링크 등은 제외). 데이터에서 빠져 즐겨찾기
            // 스냅샷으로만 열린 장소도 제외 — 최근 본 목록(places 조회)엔 안 보이면서 자리만 차지한다.
            if (!place.isOutdated) recentPlaceStore.add(placeId)
            // 즐겨찾기 구독은 장소 로드 뒤에 시작해야 한다. 별도 코루틴으로 돌리면 첫 emit 이
            // place==null 시점에 도착해 유실되고, 이미 즐겨찾기한 장소가 빈 하트로 표시된다.
            // observeFavorites: 하트 상태에 더해 이 장소에 남긴 메모까지 함께 반영한다.
            repository.observeFavorites().collect { favorites ->
                val favorite = favorites.find { it.id == placeId }
                _uiState.update { state ->
                    state.copy(
                        place = state.place?.copy(
                            isFavorite = favorite != null,
                            memo = favorite?.memo,
                        )
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    /** 즐겨찾기 메모 저장 (빈 문자열이면 삭제) */
    fun saveMemo(memo: String) {
        viewModelScope.launch { repository.updateFavoriteMemo(placeId, memo) }
    }
}
