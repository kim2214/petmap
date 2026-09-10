package com.kimdev.petmap.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionTest {

    @Test
    fun `prefix 는 토큰 뒤 공백까지 포함해 다른 토큰과 섞이지 않는다`() {
        assertEquals("경기도 ", Region("경기도").prefix)
        assertEquals("경기도 고양시 ", Region("경기도", "고양시").prefix)
        assertTrue("경기도 고양시 덕양구 동세로 19".startsWith(Region("경기도", "고양시").prefix))
        assertFalse("경기도 고양시청로 1".startsWith(Region("경기도", "고양시").prefix))
    }

    @Test
    fun `shortLabel 은 시도를 줄이고 시군구를 붙인다`() {
        assertEquals("경기 고양시", Region("경기도", "고양시").shortLabel)
        assertEquals("서울", Region("서울특별시").shortLabel)
        assertEquals("전남·광주 여수시", Region("전남광주통합특별시", "여수시").shortLabel)
        assertEquals("모르는도", Region("모르는도").shortLabel) // 미등록 이름은 그대로
    }

    @Test
    fun `시군구 토큰 판정`() {
        assertTrue(Region.isSigunguToken("고양시"))
        assertTrue(Region.isSigunguToken("북구"))
        assertTrue(Region.isSigunguToken("구례군"))
        assertFalse(Region.isSigunguToken("조치원읍"))
        assertFalse(Region.isSigunguToken("한누리대로"))
        assertFalse(Region.isSigunguToken("구"))
    }
}
