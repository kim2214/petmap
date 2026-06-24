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

/**
 * 한국문화정보원 "반려동물 동반가능 문화시설" 데이터셋의 카테고리3 기준.
 * (동물약국·동물병원·반려동물용품·미용·카페·식당·펜션/호텔·박물관/미술관/문예회관·여행지·위탁관리)
 */
enum class PlaceCategory(val label: String) {
    HOSPITAL("동물병원"),
    PHARMACY("동물약국"),
    SHOP("용품"),
    GROOMING("미용"),
    CAFE("카페"),
    RESTAURANT("식당"),
    ACCOMMODATION("숙박"),
    CULTURE("문화시설"),
    TRAVEL("여행지"),
    CARE("위탁관리"),
    ETC("기타");

    companion object {
        /** 카테고리3 문자열을 앱 카테고리로 매핑 (정확 일치 우선, 그 외 키워드 폴백) */
        fun fromRaw(raw: String?): PlaceCategory = when (raw?.trim()) {
            null, "" -> ETC
            "동물병원" -> HOSPITAL
            "동물약국" -> PHARMACY
            "반려동물용품" -> SHOP
            "미용" -> GROOMING
            "카페" -> CAFE
            "식당" -> RESTAURANT
            "펜션", "호텔" -> ACCOMMODATION
            "박물관", "미술관", "문예회관" -> CULTURE
            "여행지" -> TRAVEL
            "위탁관리" -> CARE
            else -> when {
                raw.contains("병원") -> HOSPITAL
                raw.contains("약국") -> PHARMACY
                raw.contains("용품") -> SHOP
                raw.contains("미용") -> GROOMING
                raw.contains("카페") -> CAFE
                raw.contains("식당") || raw.contains("음식") -> RESTAURANT
                raw.contains("펜션") || raw.contains("호텔") || raw.contains("숙박") -> ACCOMMODATION
                raw.contains("박물") || raw.contains("미술") || raw.contains("문예") -> CULTURE
                raw.contains("여행") -> TRAVEL
                else -> ETC
            }
        }
    }
}
