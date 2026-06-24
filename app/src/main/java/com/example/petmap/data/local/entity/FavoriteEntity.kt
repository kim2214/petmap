package com.example.petmap.data.local.entity

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
)
