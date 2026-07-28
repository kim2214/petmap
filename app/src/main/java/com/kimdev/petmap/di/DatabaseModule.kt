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
            // 다운그레이드(구 APK 사이드로드 등)만 파괴적 재생성을 허용한다.
            // 업그레이드 경로 누락은 조용히 전체 데이터(즐겨찾기 포함)를 지우는 대신
            // 예외로 드러나야 개발 단계에서 잡을 수 있다 — 전체 fallback 금지.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideFavoriteDao(db: PetMapDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaceDao(db: PetMapDatabase): PlaceDao = db.placeDao()
}
