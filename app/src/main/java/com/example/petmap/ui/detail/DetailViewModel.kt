package com.example.petmap.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.repository.PlaceRepository
import com.example.petmap.ui.navigation.Routes
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[Routes.DETAIL_ARG_ID])

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val place = repository.getPlace(placeId)
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
}
