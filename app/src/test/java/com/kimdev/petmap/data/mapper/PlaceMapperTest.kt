package com.kimdev.petmap.data.mapper

import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity
import com.kimdev.petmap.data.remote.dto.PlaceDto
import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMapperTest {

    private fun dto(
        name: String? = "행복카페",
        category: String? = "카페",
        roadAddress: String? = "서울시 강남구 1",
        lotAddress: String? = "강남동 2",
        latitude: Double? = 37.5,
        longitude: Double? = 127.0,
        phone: String? = "02-123-4567",
        operatingTime: String? = "10:00~22:00",
        closedDays: String? = "연중무휴",
        homepage: String? = "http://x",
        allowedPetSize: String? = "소형견",
        restriction: String? = "목줄 필수",
        indoorAllowed: String? = "Y",
        outdoorAllowed: String? = "N",
    ) = PlaceDto(
        name = name,
        category = category,
        roadAddress = roadAddress,
        lotAddress = lotAddress,
        latitude = latitude,
        longitude = longitude,
        phone = phone,
        operatingTime = operatingTime,
        closedDays = closedDays,
        homepage = homepage,
        allowedPetSize = allowedPetSize,
        restriction = restriction,
        indoorAllowed = indoorAllowed,
        outdoorAllowed = outdoorAllowed,
    )

    // ---- PlaceDto.toDomain : 필수값 누락 시 null ----
    @Test
    fun `toDomain returns null when latitude missing`() {
        assertNull(dto(latitude = null).toDomain())
    }

    @Test
    fun `toDomain returns null when longitude missing`() {
        assertNull(dto(longitude = null).toDomain())
    }

    @Test
    fun `toDomain returns null when name missing`() {
        assertNull(dto(name = null).toDomain())
    }

    // ---- PlaceDto.toDomain : 정상 매핑 ----
    @Test
    fun `toDomain maps fields and builds stable id`() {
        val p = dto().toDomain()!!
        assertEquals("행복카페_37.5_127.0", p.id) // 좌표+이름 기반 안정 id
        assertEquals("행복카페", p.name)
        assertEquals(PlaceCategory.CAFE, p.category)
        assertEquals("서울시 강남구 1", p.roadAddress)
        assertEquals(37.5, p.lat, 0.0)
        assertEquals(127.0, p.lng, 0.0)
        assertEquals("02-123-4567", p.phone)
        assertEquals("소형견", p.petInfo.allowedPetSize)
        assertTrue(p.petInfo.indoorAllowed)   // "Y" → true
        assertFalse(p.petInfo.outdoorAllowed) // "N" → false
    }

    @Test
    fun `toDomain falls back to lot address when road address null`() {
        val p = dto(roadAddress = null, lotAddress = "지번주소만").toDomain()!!
        assertEquals("지번주소만", p.roadAddress)
    }

    @Test
    fun `toDomain blanks become null`() {
        val p = dto(phone = "  ", operatingTime = "", homepage = " ").toDomain()!!
        assertNull(p.phone)
        assertNull(p.operatingTime)
        assertNull(p.homepage)
    }

    @Test
    fun `toDomain recognizes various truthy strings`() {
        assertTrue(dto(indoorAllowed = "가능").toDomain()!!.petInfo.indoorAllowed)
        assertTrue(dto(indoorAllowed = "true").toDomain()!!.petInfo.indoorAllowed)
        assertTrue(dto(indoorAllowed = "Y").toDomain()!!.petInfo.indoorAllowed)
        assertFalse(dto(indoorAllowed = null).toDomain()!!.petInfo.indoorAllowed)
        assertFalse(dto(indoorAllowed = "불가").toDomain()!!.petInfo.indoorAllowed)
    }

    // ---- PlaceDto.toEntity ----
    @Test
    fun `toEntity returns null when coordinates missing`() {
        assertNull(dto(latitude = null).toEntity())
        assertNull(dto(longitude = null).toEntity())
        assertNull(dto(name = null).toEntity())
    }

    @Test
    fun `toEntity stores category as enum name and maps bools`() {
        val e = dto(category = "카페", indoorAllowed = "Y", outdoorAllowed = "가능").toEntity()!!
        assertEquals("CAFE", e.category) // 컬럼엔 PlaceCategory.name 저장
        assertTrue(e.indoorAllowed)
        assertTrue(e.outdoorAllowed)
        assertEquals("행복카페_37.5_127.0", e.id)
    }

    @Test
    fun `toDomain and toEntity agree on category and id`() {
        val d = dto(category = "동물병원")
        val fromDto = d.toDomain()!!
        val fromEntity = d.toEntity()!!.toDomain()
        assertEquals(fromDto.category, fromEntity.category)
        assertEquals(PlaceCategory.HOSPITAL, fromDto.category)
        assertEquals(fromDto.id, fromEntity.id)
    }

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
