package com.kimdev.petmap.domain.model

/**
 * 반려동물 동반 조건 필터 (목록·지도 화면).
 * 여러 개를 켜면 AND 조건이며, 해당 정보가 없는 장소는 제외된다
 * (영업중 필터의 "정보 없는 장소 제외" 정책과 동일 — 잘못된 추정으로 헛걸음시키지 않는다).
 */
enum class PetFilter {
    /** 크기 제한 없음 — 원본 데이터의 "모두 가능" */
    ANY_SIZE,

    /** 실내 동반 가능 */
    INDOOR,

    /** 실외 동반 가능 */
    OUTDOOR,

    /**
     * 내 반려견(설정에 저장한 체중)이 들어갈 수 있는 곳 — `maxPetKg >= 체중`.
     * 체중이 등록되지 않았으면(myWeightKg == null) 조건 없음으로 취급한다(UI 가 등록을 먼저 유도한다).
     */
    MY_PET;

    /**
     * 클라이언트 측 판정(조회 후 후처리·테스트 페이크). SQL 판정(PlaceDao)과 같은 정책이어야 한다.
     * @param myWeightKg [MY_PET] 에만 쓰이는 내 반려견 체중
     */
    fun matches(place: Place, myWeightKg: Double? = null): Boolean = when (this) {
        ANY_SIZE -> place.petInfo.allowedPetSize == SIZE_ALL
        INDOOR -> place.petInfo.indoorAllowed == true
        OUTDOOR -> place.petInfo.outdoorAllowed == true
        MY_PET -> myWeightKg == null || place.petInfo.allowsWeight(myWeightKg)
    }

    companion object {
        /** 원본 데이터셋에서 크기 제한 없음을 뜻하는 값 (전체의 약 82%). DAO 의 SQL 필터도 이 값을 쓴다 */
        const val SIZE_ALL = "모두 가능"

        /** [MY_PET] 이 켜져 있고 체중이 등록돼 있을 때만 SQL 에 바인딩할 체중, 아니면 null(조건 없음) */
        fun effectiveWeight(filters: Set<PetFilter>, myWeightKg: Double?): Double? =
            if (MY_PET in filters) myWeightKg else null
    }
}
