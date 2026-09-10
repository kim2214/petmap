package com.kimdev.petmap.domain.model

/** 위/경도 박스. 지역 선택 시 카메라를 맞출 범위 등에 쓴다 */
data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    val centerLat: Double get() = (minLat + maxLat) / 2
    val centerLng: Double get() = (minLng + maxLng) / 2
    /** 한 점(또는 거의 한 점)인가 — fitBounds 가 최대 줌으로 파고드는 것을 막을 때 판정 */
    val isPoint: Boolean get() = maxLat - minLat < 1e-4 && maxLng - minLng < 1e-4
}
