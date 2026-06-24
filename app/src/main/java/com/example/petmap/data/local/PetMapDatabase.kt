package com.example.petmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.petmap.data.local.dao.FavoriteDao
import com.example.petmap.data.local.entity.FavoriteEntity

@Database(
    entities = [FavoriteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PetMapDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "petmap.db"
    }
}
