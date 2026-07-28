package com.kimdev.petmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity

// 스키마 JSON(app/schemas/)을 리포에 커밋한다. 엔티티 변경 시 identity_hash 가 어떻게 바뀌는지
// 추적하고, tools/build_db.py 의 ROOM_IDENTITY_HASH 와 대조하는 단일 기준이 된다.
@Database(
    entities = [FavoriteEntity::class, PlaceEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class PetMapDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun placeDao(): PlaceDao

    companion object {
        const val NAME = "petmap.db"
    }
}
