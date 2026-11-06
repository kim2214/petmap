package com.kimdev.petmap.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
)

/** 상세 화면의 일회성 이벤트 (상태에 담고 consume 하지 않는 이유는 MapEvent 참고) */
sealed interface DetailEvent {
    /** 즐겨찾기·메모 쓰기 실패(저장 공간 부족·DB I/O 오류) — 화면은 그대로, 스낵바로만 알린다 */
    data object FavoriteWriteFailed : DetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val recentPlaceStore: RecentPlaceStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[Routes.DETAIL_ARG_ID])

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            var opened = false
            // 원격 갱신(dataVersion)마다 장소를 다시 읽는다 — 상세를 연 채로 데이터가 바뀌어도 새 정보를 보여 준다.
            // combine: 즐겨찾기 구독을 장소 로드와 따로 돌리면 첫 emit 이 place==null 시점에 도착해 유실되고,
            // 이미 즐겨찾기한 장소가 빈 하트로 표시된다. observeFavorites 로 하트와 메모를 함께 반영한다.
            combine(
                repository.dataVersion.mapLatest {
                    // 로드 실패 시에도 스피너가 멈추도록 예외를 흡수한다(화면은 place==null 을 "찾을 수 없음"으로 표시).
                    runCatching { repository.getPlace(placeId) }.getOrNull()
                },
                repository.observeFavorites(),
            ) { place, favorites ->
                // 옛 공유 링크는 지금 id 가 다른 같은 장소로 열린다 → 하트·메모는 실제 장소 id 로 맞춘다
                val id = place?.id ?: placeId
                place to favorites.find { it.id == id }
            }
                .collect { (loaded, favorite) ->
                    // 보고 있는 중에 갱신으로 장소가 빠지면(즐겨찾기도 아니면) 화면을 비우지 않고 이전 정보를 둔다
                    val base = loaded ?: _uiState.value.place
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            place = base?.copy(isFavorite = favorite != null, memo = favorite?.memo),
                        )
                    }
                    if (!opened && loaded != null) {
                        opened = true
                        // 실제로 열린 장소만 "최근 본"에 남긴다(없는 ID 딥링크 등은 제외). 데이터에서 빠져 즐겨찾기
                        // 스냅샷으로만 열린 장소도 제외 — 최근 본 목록(places 조회)엔 안 보이면서 자리만 차지한다.
                        if (!loaded.isOutdated) recentPlaceStore.add(loaded.id)
                    }
                }
        }
    }

    fun toggleFavorite() {
        val place = _uiState.value.place ?: return
        writeFavorite { repository.toggleFavorite(place) }
    }

    /** 즐겨찾기 메모 저장 (빈 문자열이면 삭제) */
    fun saveMemo(memo: String) {
        val id = _uiState.value.place?.id ?: placeId // 옛 링크로 열렸으면 지금 id
        writeFavorite { repository.updateFavoriteMemo(id, memo) }
    }

    /**
     * 즐겨찾기 쓰기 공용 래퍼. DAO 예외(SQLiteFullException 등)를 삼키지 않으면 viewModelScope 의
     * 미처리 예외로 앱이 죽는다 → 로그 + [DetailEvent.FavoriteWriteFailed] 로 알리고 끝낸다.
     */
    private fun writeFavorite(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "즐겨찾기 저장 실패", e)
                _events.send(DetailEvent.FavoriteWriteFailed)
            }
        }
    }

    private companion object {
        const val TAG = "DetailViewModel"
    }
}
