package com.kimdev.petmap.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 하단 탭을 "재선택"(이미 선택된 탭을 다시 탭)했을 때의 이벤트 버스.
 * 목록/즐겨찾기는 맨 위로 스크롤, 지도는 내 위치 재센터링에 쓴다.
 *
 * SharedFlow(replay 0): 재선택 시점에 해당 탭 화면은 반드시 컴포지션에 있으므로
 * 버퍼 대기가 필요 없고, 수집자가 없던 순간의 이벤트는 버리는 게 맞다.
 */
@Singleton
class TabReselectBus @Inject constructor() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 재선택된 탭의 라우트 */
    val events: SharedFlow<String> = _events

    fun emit(route: String) {
        _events.tryEmit(route)
    }
}
