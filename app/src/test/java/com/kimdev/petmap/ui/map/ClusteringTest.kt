package com.kimdev.petmap.ui.map

import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusteringTest {

    private fun place(id: String, lat: Double, lng: Double) = Place(
        id = id,
        name = id,
        category = PlaceCategory.CAFE,
        roadAddress = "주소",
        lotAddress = "",
        lat = lat,
        lng = lng,
        phone = null,
        operatingTime = null,
        closedDays = null,
        homepage = null,
        petInfo = PetInfo(null, null, indoorAllowed = false, outdoorAllowed = false),
    )

    @Test fun `빈 목록은 빈 클러스터`() {
        assertTrue(clusterPlaces(emptyList(), 14.0).isEmpty())
    }

    @Test fun `단일 장소는 single 마커`() {
        val result = clusterPlaces(listOf(place("a", 37.5, 127.0)), 16.0)
        assertEquals(1, result.size)
        assertEquals(1, result[0].count)
        assertNotNull(result[0].single)
    }

    @Test fun `같은 좌표 두 곳은 한 클러스터로 묶임`() {
        val result = clusterPlaces(
            listOf(place("a", 37.5, 127.0), place("b", 37.5, 127.0)),
            18.0,
        )
        assertEquals(1, result.size)
        assertEquals(2, result[0].count)
        assertNull(result[0].single)
    }

    @Test fun `멀리 떨어진 두 곳은 각각 분리`() {
        // 서울 vs 부산 (약 325km) → 어떤 줌에서도 분리
        val result = clusterPlaces(
            listOf(place("seoul", 37.55, 126.97), place("busan", 35.18, 129.07)),
            12.0,
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.single != null })
    }

    @Test fun `줌 아웃하면 묶이고 줌 인하면 분리된다`() {
        // 약 1.1km 떨어진 두 곳: 셀 크기가 줌에 따라 달라져 결과가 갈린다
        val places = listOf(place("a", 37.500, 127.0), place("b", 37.510, 127.0))
        assertEquals(1, clusterPlaces(places, 8.0).size)  // 줌 아웃: 한 셀
        assertEquals(2, clusterPlaces(places, 17.0).size) // 줌 인: 분리
    }

    @Test fun `spanMeters - 단일은 0`() {
        val c = MapCluster("a", 37.5, 127.0, listOf(place("a", 37.5, 127.0)))
        assertEquals(0.0, c.spanMeters(), 0.0001)
    }

    @Test fun `spanMeters - 같은 좌표는 0`() {
        val members = listOf(place("a", 37.5, 127.0), place("b", 37.5, 127.0))
        val c = MapCluster("c", 37.5, 127.0, members)
        assertEquals(0.0, c.spanMeters(), 0.0001)
    }

    @Test fun `clusterLabel - 세 자리까지는 그대로`() {
        assertEquals("1", clusterLabel(1))
        assertEquals("48", clusterLabel(48))
        assertEquals("999", clusterLabel(999))
    }

    @Test fun `clusterLabel - 네 자리 이상은 천 단위로 축약`() {
        // 집계 클러스터는 수천까지 나오므로 원 안에 들어가도록 줄인다
        assertEquals("1.0천", clusterLabel(1000))
        assertEquals("3.9천", clusterLabel(3903))
        assertEquals("11천", clusterLabel(11_047))
    }

    @Test fun `spanMeters - 약 1km 차이는 수백m 이상`() {
        // 위도 0.009도 ≈ 1km
        val members = listOf(place("a", 37.5000, 127.0), place("b", 37.5090, 127.0))
        val c = MapCluster("c", 37.5, 127.0, members)
        assertTrue("span=${c.spanMeters()}", c.spanMeters() > 500.0)
    }
}
