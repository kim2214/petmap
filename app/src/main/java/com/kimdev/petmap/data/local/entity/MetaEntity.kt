package com.kimdev.petmap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 에셋 DB 에 동봉되는 키-값 메타데이터 (tools/build_db.py 가 기록).
 * 사용자가 쓰는 테이블이 아니므로 파괴적 재생성 때 함께 갈아엎어도 된다.
 */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
) {
    companion object {
        /** 수록 데이터 기준일 (ISO-8601 날짜, 예: 2026-08-19) */
        const val KEY_DATA_DATE = "data_date"
    }
}
