package com.kimdev.petmap.data.local

/**
 * places 테이블에 테스트 행을 삽입한다.
 * 프로덕션 DAO 에는 쓰기 API 가 없으므로(프리빌트 DB 읽기 전용) raw SQL 로 넣는다.
 */
fun PetMapDatabase.insertPlace(
    id: String,
    name: String = id,
    category: String = "CAFE",
    roadAddress: String = "서울시 어딘가 1",
    lat: Double = 37.5,
    lng: Double = 127.0,
    allowedPetSize: String? = null,
    indoorAllowed: Boolean? = false,
    outdoorAllowed: Boolean? = true,
    maxPetKg: Double? = null,
    parkingAvailable: Boolean? = null,
    operatingTime: String? = null,
    /** 기본은 이름 그대로 — 정렬 버킷을 검증하는 테스트만 명시한다 */
    sortKey: String = name,
) {
    openHelper.writableDatabase.execSQL(
        "INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        arrayOf<Any?>(
            id, name, category, roadAddress, "", lat, lng,
            // phone, operatingTime, closedDays, homepage, allowedPetSize, restriction, indoor, outdoor
            null, operatingTime, null, null, allowedPetSize, null,
            indoorAllowed?.let { if (it) 1 else 0 }, outdoorAllowed?.let { if (it) 1 else 0 },
            // v4: parkingAvailable, fee, petFee, description
            parkingAvailable?.let { if (it) 1 else 0 }, null, null, null,
            // v7: maxPetKg / v9: sortKey
            maxPetKg, sortKey,
        ),
    )
}

/** places 전체로 FTS 색인을 구축한다(프로덕션에선 에셋 동봉/마이그레이션이 하는 일). */
fun PetMapDatabase.buildFtsIndex() {
    PlaceFts.create(openHelper.writableDatabase)
    PlaceFts.rebuild(openHelper.writableDatabase)
}
