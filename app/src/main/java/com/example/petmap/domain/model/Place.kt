package com.example.petmap.domain.model

/**
 * 앱 전반에서 사용하는 도메인 모델.
 * 원격 DTO / 로컬 Entity 와 분리하여, 데이터 출처가 바뀌어도 UI 는 영향받지 않는다.
 */
data class Place(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val roadAddress: String,
    val lotAddress: String,
    val lat: Double,
    val lng: Double,
    val phone: String?,
    val operatingTime: String?,
    val closedDays: String?,
    val homepage: String?,
    val petInfo: PetInfo,
    val isFavorite: Boolean = false,
)

/** 반려동물 동반 관련 정보 */
data class PetInfo(
    /** 입장 가능한 반려동물 크기 (예: "소형견", "중형견 이하") */
    val allowedPetSize: String?,
    /** 동반 시 제한사항 */
    val restriction: String?,
    val indoorAllowed: Boolean,
    val outdoorAllowed: Boolean,
)

enum class PlaceCategory(val label: String) {
    CAFE("카페"),
    RESTAURANT("식당"),
    ACCOMMODATION("숙박"),
    CULTURE("문화시설"),
    PARK("공원/야외"),
    HOSPITAL("동물병원"),
    SHOP("펫샵/용품"),
    ETC("기타");

    companion object {
        /** 공공데이터 카테고리 문자열을 앱 카테고리로 매핑 */
        fun fromRaw(raw: String?): PlaceCategory = when {
            raw == null -> ETC
            raw.contains("카페") -> CAFE
            raw.contains("음식") || raw.contains("식당") || raw.contains("레스토랑") -> RESTAURANT
            raw.contains("숙박") || raw.contains("펜션") || raw.contains("호텔") -> ACCOMMODATION
            raw.contains("문화") || raw.contains("미술") || raw.contains("박물") -> CULTURE
            raw.contains("공원") || raw.contains("야외") -> PARK
            raw.contains("병원") -> HOSPITAL
            raw.contains("펫") || raw.contains("용품") -> SHOP
            else -> ETC
        }
    }
}
