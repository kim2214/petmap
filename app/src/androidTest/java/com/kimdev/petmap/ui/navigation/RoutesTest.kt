package com.kimdev.petmap.ui.navigation

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * placeId 는 `이름_위도_경도` 로 데이터에서 생성되므로 경로 예약문자가 섞일 수 있다.
 * (예: 카테고리 표기가 이름에 포함된 "박물관/미술관…")
 * 인코딩이 빠지면 navigate 시 경로 매칭이 깨져 크래시하므로 계약을 고정한다.
 * Uri.encode 가 안드로이드 구현이라 androidTest 로 검증한다.
 */
@RunWith(AndroidJUnit4::class)
class RoutesTest {

    @Test
    fun detail_encodesPathReservedCharacters() {
        // 경로 구분자가 남아 있으면 detail/{placeId} 패턴이 매칭되지 않아 navigate 가 크래시한다
        val segment = Routes.detail("박물관/미술관_37.5_127.0").removePrefix("$DETAIL_PREFIX/")
        assertFalse("'/' 가 인코딩되지 않았다: $segment", segment.contains('/'))
        assertFalse(segment.contains('?'))
        assertFalse(segment.contains('#'))
    }

    @Test
    fun detail_encodesQueryAndFragmentMarkers() {
        assertFalse(Routes.detail("가게?이름_1_2").removePrefix("$DETAIL_PREFIX/").contains('?'))
        assertFalse(Routes.detail("가게#1_1_2").removePrefix("$DETAIL_PREFIX/").contains('#'))
    }

    @Test
    fun detail_roundTripsThroughDecode() {
        // Navigation 이 인자를 읽을 때 디코딩하므로, 디코딩 결과가 원본과 같아야
        // DetailViewModel 이 DB 의 placeId 와 일치하는 값을 받는다.
        listOf(
            "박물관/미술관_37.5_127.0",
            "행복카페_37.5_127.0",
            "펫 프렌들리 카페_37.5_127.0", // 공백 포함(실데이터 5,436건)
            "가게#1?x_1_2",
        ).forEach { id ->
            val segment = Routes.detail(id).removePrefix("$DETAIL_PREFIX/")
            assertEquals(id, Uri.decode(segment))
        }
    }

    private companion object {
        const val DETAIL_PREFIX = "detail"
    }
}
