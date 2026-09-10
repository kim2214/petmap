package com.kimdev.petmap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 프리빌트 에셋 DB(tools/build_db.py)로 시딩되는 장소 테이블.
 * 지도 뷰포트 쿼리를 위해 위/경도에 인덱스를 둔다.
 */
@Entity(
    tableName = "places",
    indices = [
        Index(value = ["lat", "lng"]),
        Index(value = ["category"]),
        Index(value = ["sortKey"]),
    ],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val roadAddress: String,
    val lotAddress: String,
    val lat: Double,
    val lng: Double,
    val phone: String?,
    val operatingTime: String?,
    val closedDays: String?,
    val homepage: String?,
    val allowedPetSize: String?,
    val restriction: String?,
    // v6: nullable. 원본에 실내/실외 컬럼이 없는 데이터셋(액티비티·식약처 음식점)은 null = 정보 없음.
    // 이전엔 NOT NULL 0 이라 "동반 불가"로 표시되는 오류가 있었다(레저 996건).
    val indoorAllowed: Boolean?,
    val outdoorAllowed: Boolean?,
    // v4 추가 컬럼. 원본에 값이 없거나 노이즈("변동", 카테고리명과 동일한 설명 등)면
    // build_db.py 가 null 로 정제해 넣는다. 구버전 설치(마이그레이션 경로)는 전부 null.
    val parkingAvailable: Boolean?,
    val fee: String?,
    val petFee: String?,
    val description: String?,
    /**
     * v7: allowedPetSize 를 build_db.py 가 숫자로 정규화한 입장 가능 최대 체중(kg).
     * "모두 가능"/"대형" 은 999, "10kg 미만" 은 9.99, "소형" 10, "중형" 25. null = 정보 없음(필터에서 제외).
     * "내 반려견" 필터가 SQL 에서 `maxPetKg >= :myPetKg` 로 쓴다.
     */
    val maxPetKg: Double?,
    /**
     * v9: 이름순 정렬 키(build_db.py sort_key). [버킷: 한글 0·영문 1·숫자 2·기타 3][법인 표기·앞 기호를 뗀 소문자 이름].
     * `ORDER BY name` 은 UTF-8 바이트 순이라 "(주)…"·숫자·영문이 한글 앞에 몰렸다.
     */
    val sortKey: String,
)
