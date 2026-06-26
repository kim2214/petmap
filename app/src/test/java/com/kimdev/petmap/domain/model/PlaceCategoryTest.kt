package com.kimdev.petmap.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCategoryTest {

    @Test fun `정확 일치 카테고리3 매핑`() {
        assertEquals(PlaceCategory.HOSPITAL, PlaceCategory.fromRaw("동물병원"))
        assertEquals(PlaceCategory.PHARMACY, PlaceCategory.fromRaw("동물약국"))
        assertEquals(PlaceCategory.SHOP, PlaceCategory.fromRaw("반려동물용품"))
        assertEquals(PlaceCategory.GROOMING, PlaceCategory.fromRaw("미용"))
        assertEquals(PlaceCategory.CAFE, PlaceCategory.fromRaw("카페"))
        assertEquals(PlaceCategory.RESTAURANT, PlaceCategory.fromRaw("식당"))
        assertEquals(PlaceCategory.TRAVEL, PlaceCategory.fromRaw("여행지"))
        assertEquals(PlaceCategory.CARE, PlaceCategory.fromRaw("위탁관리"))
    }

    @Test fun `펜션 호텔은 숙박으로`() {
        assertEquals(PlaceCategory.ACCOMMODATION, PlaceCategory.fromRaw("펜션"))
        assertEquals(PlaceCategory.ACCOMMODATION, PlaceCategory.fromRaw("호텔"))
    }

    @Test fun `박물관 미술관 문예회관은 문화시설로`() {
        assertEquals(PlaceCategory.CULTURE, PlaceCategory.fromRaw("박물관"))
        assertEquals(PlaceCategory.CULTURE, PlaceCategory.fromRaw("미술관"))
        assertEquals(PlaceCategory.CULTURE, PlaceCategory.fromRaw("문예회관"))
    }

    @Test fun `키워드 포함 폴백 매핑`() {
        assertEquals(PlaceCategory.HOSPITAL, PlaceCategory.fromRaw("24시동물병원"))
        assertEquals(PlaceCategory.CAFE, PlaceCategory.fromRaw("애견카페"))
    }

    @Test fun `공백 트림 처리`() {
        assertEquals(PlaceCategory.CAFE, PlaceCategory.fromRaw("  카페 "))
    }

    @Test fun `미지 또는 null은 기타`() {
        assertEquals(PlaceCategory.ETC, PlaceCategory.fromRaw(null))
        assertEquals(PlaceCategory.ETC, PlaceCategory.fromRaw(""))
        assertEquals(PlaceCategory.ETC, PlaceCategory.fromRaw("알수없는분류"))
    }
}
