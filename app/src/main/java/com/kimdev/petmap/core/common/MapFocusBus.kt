package com.kimdev.petmap.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 화면 간 "이 장소를 지도에서 보기" 요청 전달용 앱 스코프 버스.
 * 상세 화면이 placeId 를 넣고 지도 탭으로 전환하면, 지도 화면이 이를 소비해 카메라를 이동한다.
 */
@Singleton
class MapFocusBus @Inject constructor() {
    private val _targetPlaceId = MutableStateFlow<String?>(null)

    /** 지도 화면이 관찰할 포커스 대상 placeId. 변경은 request()/consume() 으로만. */
    val targetPlaceId: StateFlow<String?> = _targetPlaceId.asStateFlow()

    fun request(placeId: String) {
        _targetPlaceId.value = placeId
    }

    fun consume() {
        _targetPlaceId.value = null
    }
}
