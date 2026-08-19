package com.kimdev.petmap.domain.model

/**
 * 앱 전반에서 사용하는 도메인 모델.
 * 로컬 Entity 와 분리하여, 데이터 출처가 바뀌어도 UI 는 영향받지 않는다.
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
    /** 주차 가능 여부. null = 정보 없음 */
    val parkingAvailable: Boolean? = null,
    /** 입장/이용료 정보 (실질 값이 있을 때만, "변동" 등 노이즈는 null) */
    val fee: String? = null,
    /** 반려동물 동반 추가 요금 */
    val petFee: String? = null,
    /** 장소 설명 (카테고리명 반복 등 노이즈는 null) */
    val description: String? = null,
    val isFavorite: Boolean = false,
    /** 즐겨찾기에 남긴 메모 (즐겨찾기가 아닐 땐 null) */
    val memo: String? = null,
    /** 사용자 위치 기준 거리(m). 위치를 모르면 null */
    val distanceMeters: Double? = null,
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
 * 한국문화정보원 데이터셋 기준 카테고리.
 * - "반려동물 동반가능 문화시설"의 카테고리3
 *   (동물약국·동물병원·반려동물용품·미용·카페·식당·펜션/호텔·박물관/미술관/문예회관·여행지·위탁관리)
 * - "문화 여가 활동 시설(액티비티)"의 레저/체육/공원 → SPORTS
 *
 * 원문 카테고리 → enum 매핑은 DB 생성 시점에 tools/build_db.py(category_name)가 수행하고,
 * DB 에는 enum name 이 저장된다.
 */
enum class PlaceCategory {
    HOSPITAL,
    PHARMACY,
    SHOP,
    GROOMING,
    CAFE,
    RESTAURANT,
    ACCOMMODATION,
    CULTURE,
    TRAVEL,
    CARE,
    SPORTS,
    ETC,
}
