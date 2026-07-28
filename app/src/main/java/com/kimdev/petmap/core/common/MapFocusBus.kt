package com.kimdev.petmap.core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 화면 간 "이 장소를 지도에서 보기" 요청 전달용 앱 스코프 버스.
 * 상세 화면이 placeId 를 넣고 지도 탭으로 전환하면, 지도 화면이 이를 소비해 카메라를 이동한다.
 *
 * 상태(StateFlow)가 아니라 일회성 이벤트(Channel)로 다룬다. StateFlow 로 두면
 * (a) 같은 placeId 를 연달아 요청할 때 값이 같아 재방출되지 않고,
 * (b) 소비자가 없는 동안 값이 남아 있어 나중에 엉뚱한 시점에 처리된다.
 * Channel 은 각 요청이 정확히 한 번 전달되고, 지도 화면이 아직 없으면 버퍼에서 대기한다.
 */
@Singleton
class MapFocusBus @Inject constructor() {
    // 지도 화면이 없을 때도 최근 요청 1건은 대기시킨다(탭 전환 직후 전달됨).
    private val _requests = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 지도 화면(ViewModel)이 수집할 포커스 요청. 각 요청은 한 번만 전달된다. */
    val requests: Flow<String> = _requests.receiveAsFlow()

    fun request(placeId: String) {
        _requests.trySend(placeId)
    }
}
