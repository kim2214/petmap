package com.kimdev.petmap.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.core.common.MapFocusBus
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
    private val mapFocusBus: MapFocusBus,
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
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.update { state ->
                    state.copy(place = state.place?.copy(isFavorite = state.place.id in ids))
                }
            }
        }
    }

    fun toggleFavorite() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch { repository.toggleFavorite(place) }
    }

    /** 이 장소를 지도에서 보도록 요청 (지도 화면이 소비) */
    fun showOnMap() {
        mapFocusBus.request(placeId)
    }
}
