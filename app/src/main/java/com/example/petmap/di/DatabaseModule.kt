package com.example.petmap.di

import android.content.Context
import androidx.room.Room
import com.example.petmap.data.local.PetMapDatabase
import com.example.petmap.data.local.dao.FavoriteDao
import com.example.petmap.data.local.dao.PlaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PetMapDatabase =
        Room.databaseBuilder(context, PetMapDatabase::class.java, PetMapDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFavoriteDao(db: PetMapDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaceDao(db: PetMapDatabase): PlaceDao = db.placeDao()
}
