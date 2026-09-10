package com.kimdev.petmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.MetaDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.MetaEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity

// 스키마 JSON(app/schemas/)을 리포에 커밋한다. 엔티티 변경 시 identity_hash 가 어떻게 바뀌는지
// 추적하고, tools/build_db.py 의 ROOM_IDENTITY_HASH 와 대조하는 단일 기준이 된다.
@Database(
    entities = [FavoriteEntity::class, PlaceEntity::class, MetaEntity::class],
    version = PetMapDatabase.VERSION,
    exportSchema = true,
)
abstract class PetMapDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun placeDao(): PlaceDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "petmap.db"

        /**
         * 에셋 DB(tools/build_db.py 의 DB_VERSION)와 항상 같아야 한다.
         *
         * 데이터 갱신 전략: 마이그레이션을 쓰지 않는다. 버전을 올리면 Room 이 기존 DB 파일을 지우고
         * 에셋을 다시 복사한다(파괴적 재생성 — [RECREATED_FROM]). 사용자 데이터인 즐겨찾기는
         * [FavoriteBackup] 미러가 재생성 전에 보존하고 PlaceRepositoryImpl.ensureSeeded 가 복원한다.
         * 따라서 장소 데이터만 갱신할 때도 이 버전을 올려야 기존 설치에 새 데이터가 전달된다.
         */
        const val VERSION = 6

        /**
         * 재생성으로 처리하는 구버전 목록. 버전을 올릴 때 직전 버전을 여기에 추가한다
         * (명시 목록으로 두는 이유: 실수로 빠뜨린 버전은 예외로 드러나야 개발 단계에서 잡힌다).
         */
        val RECREATED_FROM = intArrayOf(1, 2, 3, 4, 5)
    }
}
