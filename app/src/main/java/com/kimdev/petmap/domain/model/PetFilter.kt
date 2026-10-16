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
    MY_PET,

    /**
     * 대형견 가능 — 중형 상한(25kg)을 넘는 개도 들어갈 수 있는 곳(`maxPetKg > 25`, "모두 가능"·"대형"·"30kg 미만" 등).
     * 체중 등록 없이 쓰는 빠른 조건이라 SQL 에선 [effectiveWeight] 로 체중 하한에 합쳐진다.
     */
    LARGE_DOG,

    /** 주차 가능 (원본 데이터 "주차 가능여부" = Y) */
    PARKING,

    /**
     * 24시간 운영 — 동반 조건은 아니지만 같은 칩 줄·같은 SQL 경로(LIMIT 전 필터, 지도 집계 개수 반영)를 쓴다.
     * 판정 규칙은 PlaceDao 의 OPEN_24H_SQL 과 같다.
     */
    OPEN_24H;

    /**
     * 클라이언트 측 판정(조회 후 후처리·테스트 페이크). SQL 판정(PlaceDao)과 같은 정책이어야 한다.
     * @param myWeightKg [MY_PET] 에만 쓰이는 내 반려견 체중
     */
    fun matches(place: Place, myWeightKg: Double? = null): Boolean = when (this) {
        ANY_SIZE -> place.petInfo.allowedPetSize == SIZE_ALL
        INDOOR -> place.petInfo.indoorAllowed == true
        OUTDOOR -> place.petInfo.outdoorAllowed == true
        MY_PET -> myWeightKg == null || place.petInfo.allowsWeight(myWeightKg)
        LARGE_DOG -> place.petInfo.allowsWeight(LARGE_DOG_MIN_KG)
        PARKING -> place.parkingAvailable == true
        OPEN_24H -> place.operatingTime?.let { ot -> OPEN_24H_HOURS.any { ot.contains(it) } }
            ?: place.name.contains("24시")
    }

    companion object {
        /** 원본 데이터셋에서 크기 제한 없음을 뜻하는 값 (전체의 약 82%). DAO 의 SQL 필터도 이 값을 쓴다 */
        const val SIZE_ALL = "모두 가능"

        /**
         * 대형견 판정 하한. build_db.py 가 "중형"을 25kg 로 숫자화하므로 그보다 무거운 개가 들어갈 수 있어야 한다
         * ("N kg 미만" → N-0.01 과 같은 0.01 단위 관례).
         */
        const val LARGE_DOG_MIN_KG = 25.01

        /** 칩 표시 순서 (목록·지도 공용) */
        // 24시는 영업중 칩 바로 뒤에 오도록 맨 앞(급할 때 찾는 동물병원)
        val CHIP_ORDER = listOf(OPEN_24H, MY_PET, LARGE_DOG, ANY_SIZE, INDOOR, OUTDOOR, PARKING)

        /** [OPEN_24H] 의 영업시간 표기 (OPEN_24H_SQL 의 LIKE 패턴과 같다) */
        private val OPEN_24H_HOURS = listOf("24시간", "상시", "00:00~24:00", "00:00-24:00", "00:00~00:00")

        /**
         * SQL 의 `maxPetKg >= ?` 에 바인딩할 체중 하한, 조건이 없으면 null.
         * [MY_PET](체중 등록 시)과 [LARGE_DOG] 가 함께 켜지면 더 엄격한(큰) 값 하나로 합친다.
         */
        fun effectiveWeight(filters: Set<PetFilter>, myWeightKg: Double?): Double? =
            listOfNotNull(
                myWeightKg?.takeIf { MY_PET in filters },
                LARGE_DOG_MIN_KG.takeIf { LARGE_DOG in filters },
            ).maxOrNull()
    }
}
