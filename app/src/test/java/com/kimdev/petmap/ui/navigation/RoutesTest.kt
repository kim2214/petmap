package com.kimdev.petmap.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {

    @Test
    fun `HTTPS App Link 에서 장소 id 를 디코딩해 꺼낸다`() {
        assertEquals(
            "행복카페_37.5_127.0",
            Routes.placeIdFromDeepLink("https://kim2214.github.io/petmap/place?id=%ED%96%89%EB%B3%B5%EC%B9%B4%ED%8E%98_37.5_127.0"),
        )
        // 다른 쿼리가 앞에 있어도, 값에 '/' 가 섞여 있어도 id 만 꺼낸다
        assertEquals("a/b?c", Routes.placeIdFromDeepLink("https://kim2214.github.io/petmap/place?utm=x&id=a%2Fb%3Fc"))
    }

    @Test
    fun `구버전 petmap 스킴도 읽는다`() {
        assertEquals("행복카페_37.5_127.0", Routes.placeIdFromDeepLink("petmap://place/%ED%96%89%EB%B3%B5%EC%B9%B4%ED%8E%98_37.5_127.0"))
    }

    @Test
    fun `우리 링크가 아니거나 id 가 없으면 null`() {
        assertNull(Routes.placeIdFromDeepLink("https://kim2214.github.io/privacy-policy.html"))
        assertNull(Routes.placeIdFromDeepLink("https://example.com/petmap/place?id=x"))
        assertNull(Routes.placeIdFromDeepLink("https://kim2214.github.io/petmap/place"))
        assertNull(Routes.placeIdFromDeepLink("https://kim2214.github.io/petmap/place?id="))
        assertNull(Routes.placeIdFromDeepLink("petmap://other/x"))
        assertNull(Routes.placeIdFromDeepLink("not a url"))
    }
}
