package com.kimdev.petmap.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MetaDao {
    @Query("SELECT `value` FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?
}
