package com.kimdev.petmap.data.mapper

import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity
import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMapperTest {

    // ---- PlaceEntity.toDomain ----
    private fun entity(category: String) = PlaceEntity(
        id = "id1",
        name = "가게",
        category = category,
        roadAddress = "도로명",
        lotAddress = "지번",
        lat = 37.5,
        lng = 127.0,
        phone = null,
        operatingTime = null,
        closedDays = null,
        homepage = null,
        allowedPetSize = null,
        restriction = null,
        indoorAllowed = true,
        outdoorAllowed = false,
        parkingAvailable = null,
        fee = null,
        petFee = null,
        description = null,
    )

    @Test
    fun `entity toDomain parses valid category`() {
        assertEquals(PlaceCategory.RESTAURANT, entity("RESTAURANT").toDomain().category)
    }

    @Test
    fun `entity toDomain falls back to ETC on unknown category`() {
        assertEquals(PlaceCategory.ETC, entity("NOT_A_CATEGORY").toDomain().category)
    }

    // ---- 즐겨찾기 round-trip ----
    @Test
    fun `favorite entity round trip keeps core fields and marks favorite`() {
        val place = Place(
            id = "p1",
            name = "즐겨찾기가게",
            category = PlaceCategory.SHOP,
            roadAddress = "도로명주소",
            lotAddress = "지번주소",
            lat = 37.1,
            lng = 127.2,
            phone = "010-0000-0000",
            operatingTime = "10:00~20:00",
            closedDays = null,
            homepage = null,
            petInfo = PetInfo("중형견", null, indoorAllowed = true, outdoorAllowed = true),
        )
        val fav = place.toFavoriteEntity()
        assertEquals("p1", fav.id)
        assertEquals("SHOP", fav.category)
        assertEquals("도로명주소", fav.roadAddress)

        val back = fav.toDomain()
        assertEquals("즐겨찾기가게", back.name)
        assertEquals(PlaceCategory.SHOP, back.category)
        assertEquals(37.1, back.lat, 0.0)
        assertEquals("", back.lotAddress) // 즐겨찾기는 최소 정보만 저장
        assertTrue(back.isFavorite)
    }

    @Test
    fun `favorite entity toDomain falls back to ETC on unknown category`() {
        val fav = FavoriteEntity("f1", "가게", "BROKEN", "주소", 37.5, 127.0, null)
        assertEquals(PlaceCategory.ETC, fav.toDomain().category)
    }
}
