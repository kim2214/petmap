package com.kimdev.petmap.domain.model

/**
 * 저줌 지도 개요용 그리드 집계 셀. 뷰포트를 격자로 나눠 셀별 대표 좌표(평균)와 개수만 담는다.
 * 개별 장소 로우를 반환하지 않으므로 화면 전역을 저비용으로 커버할 수 있다.
 */
data class GeoClusterCell(
    val lat: Double,
    val lng: Double,
    val count: Int,
    /** 셀의 격자 경계 — 탭 시 이 범위를 재조회해 목록으로 펼치는 데 쓴다 */
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)
