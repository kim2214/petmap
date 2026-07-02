package com.kimdev.petmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity

@Database(
    entities = [FavoriteEntity::class, PlaceEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class PetMapDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun placeDao(): PlaceDao

    companion object {
        const val NAME = "petmap.db"
    }
}
