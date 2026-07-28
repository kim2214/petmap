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
        Index(value = ["name"]),
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
    val indoorAllowed: Boolean,
    val outdoorAllowed: Boolean,
)
