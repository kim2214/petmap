package com.kimdev.petmap.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamePlaceMatcherTest {

    @Test
    fun `옛 id 에서 이름과 좌표를 꺼낸다 - 이름에 밑줄이 있어도 뒤 두 토큰만 좌표`() {
        assertEquals(Triple("(주)행복카페", 37.5, 127.0), SamePlaceMatcher.parseLegacyId("(주)행복카페_37.5_127.0"))
        assertEquals(Triple("카페_언더바", 37.4805094, 127.1232611), SamePlaceMatcher.parseLegacyId("카페_언더바_37.4805094_127.1232611"))
    }

    @Test
    fun `형식이 아니면 null`() {
        assertNull(SamePlaceMatcher.parseLegacyId("abc"))
        assertNull(SamePlaceMatcher.parseLegacyId("카페_37.5"))
        assertNull(SamePlaceMatcher.parseLegacyId("카페_x_127.0"))
        assertNull(SamePlaceMatcher.parseLegacyId("_37.5_127.0"))
        assertNull(SamePlaceMatcher.parseLegacyId("카페_137.5_127.0")) // 위도 범위 밖
    }

    @Test
    fun `정규화는 build_db normalize_name 과 같다`() {
        // 기대값은 python3 tools/build_db.py 의 normalize_name 출력
        mapOf(
            "(주)행복카페" to "행복카페",
            "행복 카페(강남점)" to "행복카페",
            "주식회사 셀라스" to "셀라스",
            "㈜커피빈코리아 송파파크하비오점" to "커피빈코리아송파파크하비오점",
            "CU 편의점!" to "cu편의점",
            "24시 SNC 동물메디컬센터" to "24시snc동물메디컬센터",
            "카페_언더바" to "카페언더바",
        ).forEach { (input, expected) -> assertEquals(input, expected, SamePlaceMatcher.normalizeName(input)) }
    }

    private data class P(val id: String, val name: String, val lat: Double, val lng: Double)

    private fun pick(name: String, lat: Double, lng: Double, vararg c: P) =
        SamePlaceMatcher.pick(name, lat, lng, c.toList(), { it.name }, { it.lat }, { it.lng })?.id

    @Test
    fun `같은 정규화 이름 중 150m 안의 가장 가까운 곳`() {
        assertEquals(
            "close",
            pick(
                "(주)행복카페", 37.5, 127.0,
                P("far", "행복카페", 37.5010, 127.0), // 약 111m
                P("close", "행복카페", 37.5002, 127.0), // 약 22m
                P("other", "다른카페", 37.5, 127.0),
            ),
        )
    }

    @Test
    fun `150m 밖이거나 이름이 다르면 없음`() {
        assertNull(pick("행복카페", 37.5, 127.0, P("a", "행복카페", 37.502, 127.0))) // 약 222m
        assertNull(pick("행복카페", 37.5, 127.0, P("a", "불행카페", 37.5, 127.0)))
        assertNull(pick("!!!", 37.5, 127.0, P("a", "!!!", 37.5, 127.0))) // 정규화하면 빈 이름
    }
}
