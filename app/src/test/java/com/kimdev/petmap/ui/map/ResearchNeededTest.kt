package com.kimdev.petmap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchNeededTest {

    // 기준: 줌 15(조회 반경 1.5km), 45% → 약 675m

    @Test fun `같은 위치 같은 줌이면 재검색 불필요`() {
        assertFalse(
            isResearchNeeded(37.5, 127.0, 15.0, 37.5, 127.0, 15.0),
        )
    }

    @Test fun `반경 절반 이내 이동은 재검색 불필요`() {
        // 0.001도 ≈ 111m (< 675m)
        assertFalse(
            isResearchNeeded(37.5, 127.0, 15.0, 37.501, 127.0, 15.0),
        )
    }

    @Test fun `반경 절반 넘게 이동하면 재검색 필요`() {
        // 0.01도 ≈ 1113m (> 675m)
        assertTrue(
            isResearchNeeded(37.5, 127.0, 15.0, 37.51, 127.0, 15.0),
        )
    }

    @Test fun `줌 단계가 바뀌면 같은 위치라도 재검색 필요`() {
        // 줌 15(반경 1.5km) → 줌 13(반경 4km): 반경 버킷 변경
        assertTrue(
            isResearchNeeded(37.5, 127.0, 15.0, 37.5, 127.0, 13.0),
        )
    }

    @Test fun `같은 반경 버킷 내 줌 변화는 재검색 불필요`() {
        // 줌 15, 16 모두 반경 1.5km
        assertFalse(
            isResearchNeeded(37.5, 127.0, 15.0, 37.5, 127.0, 16.0),
        )
    }

    @Test fun `radiusForZoom 줌 구간별 반경`() {
        assertEquals(1.5, radiusForZoom(16.0), 0.0)
        assertEquals(1.5, radiusForZoom(15.0), 0.0)
        assertEquals(4.0, radiusForZoom(14.0), 0.0)
        assertEquals(12.0, radiusForZoom(11.0), 0.0)
        assertEquals(40.0, radiusForZoom(9.0), 0.0)
        assertEquals(120.0, radiusForZoom(7.0), 0.0)
    }
}
