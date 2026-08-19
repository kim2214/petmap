package com.kimdev.petmap.domain.model

/**
 * 반려동물 동반 조건 필터 (목록 화면).
 * 여러 개를 켜면 AND 조건이며, 해당 정보가 없는 장소는 제외된다
 * (영업중 필터의 "정보 없는 장소 제외" 정책과 동일 — 잘못된 추정으로 헛걸음시키지 않는다).
 */
enum class PetFilter {
    /** 크기 제한 없음 — 원본 데이터의 "모두 가능" */
    ANY_SIZE,

    /** 실내 동반 가능 */
    INDOOR,

    /** 실외 동반 가능 */
    OUTDOOR;

    fun matches(place: Place): Boolean = when (this) {
        ANY_SIZE -> place.petInfo.allowedPetSize == SIZE_ALL
        INDOOR -> place.petInfo.indoorAllowed
        OUTDOOR -> place.petInfo.outdoorAllowed
    }

    companion object {
        /** 원본 데이터셋에서 크기 제한 없음을 뜻하는 값 (전체의 약 91%) */
        private const val SIZE_ALL = "모두 가능"
    }
}
