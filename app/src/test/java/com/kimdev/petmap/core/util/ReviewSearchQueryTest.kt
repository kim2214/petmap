package com.kimdev.petmap.core.util

import com.kimdev.petmap.fake.testPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSearchQueryTest {

    @Test
    fun `시도 시군구로 좁힌 상호 검색어를 만든다`() {
        val place = testPlace("p", name = "가가약국", roadAddress = "대구광역시 달서구 월배로 183")
        assertEquals("대구광역시 달서구 가가약국", reviewSearchQuery(place))
    }

    @Test
    fun `주소 공백이 여러 개거나 비어 있어도 깨지지 않는다`() {
        assertEquals(
            "경기도 고양시 카페",
            reviewSearchQuery(testPlace("p", name = " 카페 ", roadAddress = "  경기도   고양시  덕양구 1")),
        )
        assertEquals("카페", reviewSearchQuery(testPlace("p", name = "카페", roadAddress = "")))
    }
}
