package com.kimdev.petmap.fake

import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory

/** 테스트용 Place 생성 헬퍼. 필요한 필드만 지정한다. */
fun testPlace(
    id: String,
    name: String = id,
    category: PlaceCategory = PlaceCategory.ETC,
    lat: Double = 37.5,
    lng: Double = 127.0,
    operatingTime: String? = null,
    closedDays: String? = null,
    allowedPetSize: String? = null,
    indoorAllowed: Boolean = false,
    outdoorAllowed: Boolean = false,
): Place = Place(
    id = id,
    name = name,
    category = category,
    roadAddress = "도로명 $id",
    lotAddress = "지번 $id",
    lat = lat,
    lng = lng,
    phone = null,
    operatingTime = operatingTime,
    closedDays = closedDays,
    homepage = null,
    petInfo = PetInfo(allowedPetSize, null, indoorAllowed = indoorAllowed, outdoorAllowed = outdoorAllowed),
)
