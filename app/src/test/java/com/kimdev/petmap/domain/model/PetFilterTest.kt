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
    fun `대형견 필터는 중형 상한(25kg)을 넘는 곳만 통과한다`() {
        assertTrue(PetFilter.LARGE_DOG.matches(testPlace("all", maxPetKg = PetInfo.NO_LIMIT_KG)))
        assertTrue(PetFilter.LARGE_DOG.matches(testPlace("under30", maxPetKg = 29.99)))
        assertFalse(PetFilter.LARGE_DOG.matches(testPlace("medium", maxPetKg = 25.0)))
        assertFalse(PetFilter.LARGE_DOG.matches(testPlace("unknown")))
    }

    @Test
    fun `주차 필터는 주차 가능만 통과하고 정보 없음은 제외한다`() {
        assertTrue(PetFilter.PARKING.matches(testPlace("y", parkingAvailable = true)))
        assertFalse(PetFilter.PARKING.matches(testPlace("n", parkingAvailable = false)))
        assertFalse(PetFilter.PARKING.matches(testPlace("unknown")))
    }

    @Test
    fun `effectiveWeight 는 내 아이와 대형견 중 더 엄격한 하한을 쓴다`() {
        val large = PetFilter.LARGE_DOG_MIN_KG
        assertEquals(large, PetFilter.effectiveWeight(setOf(PetFilter.LARGE_DOG), null))
        assertEquals(large, PetFilter.effectiveWeight(setOf(PetFilter.LARGE_DOG, PetFilter.MY_PET), 5.0))
        assertEquals(30.0, PetFilter.effectiveWeight(setOf(PetFilter.LARGE_DOG, PetFilter.MY_PET), 30.0))
        // 체중 미등록이어도 대형견 조건은 걸린다
        assertEquals(large, PetFilter.effectiveWeight(setOf(PetFilter.LARGE_DOG, PetFilter.MY_PET), null))
    }

    @Test
    fun `칩 순서는 모든 필터를 한 번씩 담는다`() {
        assertEquals(PetFilter.entries.toSet(), PetFilter.CHIP_ORDER.toSet())
        assertEquals(PetFilter.entries.size, PetFilter.CHIP_ORDER.size)
    }

    @Test
    fun `동반 조건이 전부 미상이면 isUnknown`() {
        assertTrue(PetInfo(null, null, null, null).isUnknown)
        assertFalse(PetInfo(null, null, indoorAllowed = false, outdoorAllowed = null).isUnknown)
        assertFalse(PetInfo("소형", null, null, null).isUnknown)
    }

    @Test
    fun `24시는 영업시간 표기로, 시간 정보가 없을 때만 상호로 판정한다`() {
        assertTrue(PetFilter.OPEN_24H.matches(testPlace("a", operatingTime = "매일 00:00~24:00")))
        assertTrue(PetFilter.OPEN_24H.matches(testPlace("b", operatingTime = "상시 개방")))
        assertTrue(PetFilter.OPEN_24H.matches(testPlace("c", name = "24시 튼튼동물병원")))
        assertFalse(PetFilter.OPEN_24H.matches(testPlace("d", name = "24시 밤동물병원", operatingTime = "매일 09:00~21:00")))
        assertFalse(PetFilter.OPEN_24H.matches(testPlace("e", operatingTime = "연중무휴 10:00~22:00")))
        assertEquals(PetFilter.OPEN_24H, PetFilter.CHIP_ORDER.first())
    }
}
