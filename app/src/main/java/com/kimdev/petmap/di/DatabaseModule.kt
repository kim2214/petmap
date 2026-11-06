package com.kimdev.petmap.di

import android.content.Context
import com.kimdev.petmap.data.local.FavoriteBackup
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.MetaDao
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
    fun provideDatabase(
        @ApplicationContext context: Context,
        favoriteBackup: FavoriteBackup,
    ): PetMapDatabase =
        // 파일 I/O 없이 구성만 한다 — 구버전 즐겨찾기 보존은 첫 open 직전에 백그라운드에서 돈다(build 주석)
        PetMapDatabase.build(context, PetMapDatabase.NAME, favoriteBackup)

    @Provides
    fun provideFavoriteDao(db: PetMapDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaceDao(db: PetMapDatabase): PlaceDao = db.placeDao()

    @Provides
    fun provideMetaDao(db: PetMapDatabase): MetaDao = db.metaDao()
}
