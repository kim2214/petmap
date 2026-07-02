package com.kimdev.petmap.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: 이름/주소 검색을 LIKE '%..%' 풀스캔 대신 FTS4 로 전환.
 *
 * - 엔티티(PlaceEntity/FavoriteEntity) 스키마는 그대로라 Room identity_hash 도 불변이다.
 *   따라서 에셋(`petmap.db`)은 v2 로 유지되고, 첫 실행 시 이 마이그레이션이 FTS 색인을 구축한다.
 *   (build_db.py 는 손댈 필요 없음)
 * - 색인/검색 방식은 [PlaceFts] 참고(글자 단위 유니그램 → 한국어 부분검색 보존).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        PlaceFts.create(db)
        PlaceFts.rebuild(db)
    }
}
