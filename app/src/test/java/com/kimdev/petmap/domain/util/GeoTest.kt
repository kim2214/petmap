package com.kimdev.petmap.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test fun `같은 좌표 거리는 0`() {
        assertEquals(0.0, distanceMeters(37.5, 127.0, 37.5, 127.0), 0.0001)
    }

    @Test fun `거리는 대칭이다`() {
        val a = distanceMeters(37.5, 127.0, 35.1, 129.0)
        val b = distanceMeters(35.1, 129.0, 37.5, 127.0)
        assertEquals(a, b, 0.0001)
    }

    @Test fun `위도 0_01도는 약 1113m`() {
        // 위도 1도 ≈ 111.32km → 0.01도 ≈ 1113.2m
        val d = distanceMeters(37.5, 127.0, 37.51, 127.0)
        assertEquals(1113.2, d, 5.0)
    }

    @Test fun `서울 부산 거리는 약 325km`() {
        val seoul = Pair(37.5665, 126.9780)
        val busan = Pair(35.1796, 129.0756)
        val d = distanceMeters(seoul.first, seoul.second, busan.first, busan.second)
        assertTrue("d=$d", d in 315_000.0..335_000.0)
    }

    @Test fun `1km 미만은 m 단위`() {
        assertEquals("0m", formatDistance(0.0))
        assertEquals("500m", formatDistance(500.0))
        assertEquals("999m", formatDistance(999.0))
    }

    @Test fun `1km 이상은 소수 첫째자리 km`() {
        assertEquals("1.0km", formatDistance(1000.0))
        assertEquals("2.3km", formatDistance(2340.0))
        assertEquals("12.3km", formatDistance(12345.0))
    }
}
