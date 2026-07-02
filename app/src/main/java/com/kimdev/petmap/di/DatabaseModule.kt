package com.kimdev.petmap.di

import android.content.Context
import androidx.room.Room
import com.kimdev.petmap.data.local.MIGRATION_2_3
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.PlaceDao
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
            // 미리 시딩된 DB(에셋)를 사용 → 첫 실행 시 CSV 파싱 없이 즉시 사용
            .createFromAsset("petmap.db")
            // v2 에셋을 로드한 뒤 FTS 색인/트리거를 구축(첫 실행 1회)
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFavoriteDao(db: PetMapDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaceDao(db: PetMapDatabase): PlaceDao = db.placeDao()
}
