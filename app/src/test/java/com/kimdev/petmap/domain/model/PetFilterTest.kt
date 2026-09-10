package com.kimdev.petmap.domain.model

import com.kimdev.petmap.fake.testPlace
import org.junit.Assert.assertEquals
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
    fun `내 반려견 필터는 체중이 입장 한도 이하일 때만 통과하고 정보 없음은 제외한다`() {
        val small = testPlace("small", maxPetKg = 10.0)
        val all = testPlace("all", maxPetKg = PetInfo.NO_LIMIT_KG)
        val unknown = testPlace("unknown")
        assertTrue(PetFilter.MY_PET.matches(small, myWeightKg = 10.0))
        assertFalse(PetFilter.MY_PET.matches(small, myWeightKg = 10.5))
        assertTrue(PetFilter.MY_PET.matches(all, myWeightKg = 40.0))
        assertFalse(PetFilter.MY_PET.matches(unknown, myWeightKg = 3.0))
        // 체중 미등록이면 조건 없음
        assertTrue(PetFilter.MY_PET.matches(unknown, myWeightKg = null))
    }

    @Test
    fun `effectiveWeight 는 MY_PET 이 켜졌을 때만 체중을 돌려준다`() {
        assertEquals(5.0, PetFilter.effectiveWeight(setOf(PetFilter.MY_PET), 5.0))
        assertEquals(null, PetFilter.effectiveWeight(setOf(PetFilter.INDOOR), 5.0))
        assertEquals(null, PetFilter.effectiveWeight(setOf(PetFilter.MY_PET), null))
    }

    @Test
    fun `동반 조건이 전부 미상이면 isUnknown`() {
        assertTrue(PetInfo(null, null, null, null).isUnknown)
        assertFalse(PetInfo(null, null, indoorAllowed = false, outdoorAllowed = null).isUnknown)
        assertFalse(PetInfo("소형", null, null, null).isUnknown)
    }
}
