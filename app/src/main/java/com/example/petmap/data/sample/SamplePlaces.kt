package com.example.petmap.data.sample

import com.example.petmap.domain.model.PetInfo
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.model.PlaceCategory

/** 키 미설정 시 사용하는 더미 데이터. 실제 연동 후 제거 가능. */
object SamplePlaces {
    val list: List<Place> = listOf(
        Place(
            id = "sample_cafe_1",
            name = "멍멍이 카페 성수점",
            category = PlaceCategory.CAFE,
            roadAddress = "서울 성동구 성수이로 100",
            lotAddress = "서울 성동구 성수동",
            lat = 37.5446,
            lng = 127.0560,
            phone = "02-1234-5678",
            operatingTime = "10:00 - 22:00",
            closedDays = "연중무휴",
            homepage = null,
            petInfo = PetInfo("중형견 이하", "리드줄 필수", indoorAllowed = true, outdoorAllowed = true),
        ),
        Place(
            id = "sample_rest_1",
            name = "반려 다이닝 한강",
            category = PlaceCategory.RESTAURANT,
            roadAddress = "서울 용산구 이촌로 200",
            lotAddress = "서울 용산구 이촌동",
            lat = 37.5172,
            lng = 126.9745,
            phone = "02-2222-3333",
            operatingTime = "11:00 - 21:00",
            closedDays = "월요일",
            homepage = null,
            petInfo = PetInfo("소형견", "케이지 동반 권장", indoorAllowed = true, outdoorAllowed = false),
        ),
        Place(
            id = "sample_park_1",
            name = "올림픽공원 반려견 놀이터",
            category = PlaceCategory.PARK,
            roadAddress = "서울 송파구 올림픽로 424",
            lotAddress = "서울 송파구 방이동",
            lat = 37.5202,
            lng = 127.1216,
            phone = null,
            operatingTime = "상시 개방",
            closedDays = "연중무휴",
            homepage = null,
            petInfo = PetInfo("전 견종", "배변봉투 지참", indoorAllowed = false, outdoorAllowed = true),
        ),
    )
}
