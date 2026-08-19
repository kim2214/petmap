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

/**
 * v4: places 에 주차·요금·애견 추가요금·설명, favorites 에 메모·추가 시각.
 *
 * 컬럼만 추가한다 — 기존 설치의 places 데이터(새 컬럼 null)는 그대로다.
 * 파괴적 재생성으로 새 에셋을 재복사하면 새 정보가 채워지지만, 아직 즐겨찾기
 * 미러 백업이 배포되기 전이라 기존 사용자의 즐겨찾기가 소실된다 → ALTER 로 보존.
 * (addedAt 의 DEFAULT 0 은 엔티티 @ColumnInfo(defaultValue) 와 일치해야 함)
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE places ADD COLUMN parkingAvailable INTEGER")
        db.execSQL("ALTER TABLE places ADD COLUMN fee TEXT")
        db.execSQL("ALTER TABLE places ADD COLUMN petFee TEXT")
        db.execSQL("ALTER TABLE places ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE favorites ADD COLUMN memo TEXT")
        db.execSQL("ALTER TABLE favorites ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
        // 기존 즐겨찾기의 추가 시각은 알 수 없으므로 마이그레이션 시점으로 채운다
        // (전부 동일 값 → 정렬 tiebreaker 는 이름순)
        db.execSQL("UPDATE favorites SET addedAt = ${System.currentTimeMillis()}")
    }
}
