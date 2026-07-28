package com.kimdev.petmap.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: 이름/주소 검색을 LIKE '%..%' 풀스캔 대신 FTS4 로 전환.
 *
 * 에셋(`petmap.db`)은 v3 로 FTS 색인을 동봉하므로(tools/build_db.py) 신규 설치는 이 경로를
 * 타지 않는다. v2 DB(FTS 없음)를 가진 구버전 설치가 업그레이드할 때만 실행된다.
 * 색인/검색 방식은 [PlaceFts] 참고(글자 단위 유니그램 → 한국어 부분검색 보존).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        PlaceFts.create(db)
        PlaceFts.rebuild(db)
    }
}
