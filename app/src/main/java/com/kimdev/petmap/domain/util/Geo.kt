package com.kimdev.petmap.domain.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 두 위경도 사이의 거리(m) — Haversine. 정렬/표시/영역 판정에 공통 사용. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** 거리(m)를 사람이 읽기 좋은 문자열로. 1km 미만은 m, 그 이상은 소수 첫째자리 km. */
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()}m"
    else "${(meters / 100).roundToInt() / 10.0}km"
