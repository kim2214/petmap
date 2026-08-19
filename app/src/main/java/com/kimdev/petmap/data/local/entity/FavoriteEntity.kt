package com.kimdev.petmap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val roadAddress: String,
    val lat: Double,
    val lng: Double,
    val phone: String?,
    /** 사용자가 남긴 메모 (v4) */
    val memo: String? = null,
    /**
     * 추가 시각(epoch millis, v4) — 즐겨찾기 최신순 정렬용.
     * defaultValue 는 마이그레이션의 ALTER ... DEFAULT 0 과 일치해야 스키마 검증을 통과한다.
     */
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0L,
)
