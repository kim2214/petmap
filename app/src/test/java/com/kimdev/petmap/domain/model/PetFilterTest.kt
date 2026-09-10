package com.kimdev.petmap.domain.model

import com.kimdev.petmap.fake.testPlace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetFilterTest {

    @Test
    fun `실내외 정보가 없는 장소(null)는 실내·실외 필터에서 제외된다`() {
        val unknown = testPlace("unknown") // indoorAllowed/outdoorAllowed = null
        assertFalse(PetFilter.INDOOR.matches(unknown))
        assertFalse(PetFilter.OUTDOOR.matches(unknown))
    }

    @Test
    fun `실내외 값이 있으면 그대로 판정한다`() {
        val indoorOnly = testPlace("indoor", indoorAllowed = true, outdoorAllowed = false)
        assertTrue(PetFilter.INDOOR.matches(indoorOnly))
        assertFalse(PetFilter.OUTDOOR.matches(indoorOnly))
    }

    @Test
    fun `동반 조건이 전부 미상이면 isUnknown`() {
        assertTrue(PetInfo(null, null, null, null).isUnknown)
        assertFalse(PetInfo(null, null, indoorAllowed = false, outdoorAllowed = null).isUnknown)
        assertFalse(PetInfo("소형", null, null, null).isUnknown)
    }
}
