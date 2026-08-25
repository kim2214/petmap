package com.kimdev.petmap.di

import android.content.Context
import androidx.room.Room
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
    ): PetMapDatabase {
        // 구버전 DB 파일은 아래 fallbackToDestructiveMigrationFrom 에 의해 첫 open 시 삭제·재복사된다.
        // 그 전에 파일 안의 favorites 를 미러에 합쳐 두어야, 미러가 없던 1.6 이하 설치에서
        // 바로 올라오는 사용자의 즐겨찾기가 남는다 (미러가 있는 설치는 이미 같은 내용).
        favoriteBackup.preserveLegacyDatabase(
            context.getDatabasePath(PetMapDatabase.NAME),
            PetMapDatabase.VERSION,
        )
        return Room.databaseBuilder(context, PetMapDatabase::class.java, PetMapDatabase.NAME)
            // 미리 시딩된 DB(에셋, FTS 색인·meta 동봉)를 사용 → 첫 실행 시 파싱/색인 없이 즉시 사용
            .createFromAsset("petmap.db")
            // 구버전 설치는 마이그레이션 대신 파일 삭제 + 에셋 재복사로 최신 장소 데이터를 받는다.
            // (Room 은 파일 버전이 이 목록에 있으면 마이그레이션 없이 에셋을 다시 복사한다)
            // 즐겨찾기는 미러(FavoriteBackup)로 보존·복원된다 — PetMapDatabase.VERSION 주석 참고.
            // 목록에 없는 버전에서의 업그레이드 경로 누락은 예외로 드러나야 하므로 전체 fallback 은 쓰지 않는다.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, *PetMapDatabase.RECREATED_FROM)
            // 다운그레이드(구 APK 사이드로드 등)도 파괴적 재생성
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideFavoriteDao(db: PetMapDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaceDao(db: PetMapDatabase): PlaceDao = db.placeDao()

    @Provides
    fun provideMetaDao(db: PetMapDatabase): MetaDao = db.metaDao()
}
