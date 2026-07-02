package com.kimdev.petmap.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 이름/주소 검색용 FTS4 색인(`places_fts`) 관리.
 *
 * 한국어는 상호가 공백 없이 붙는 경우가 많아("100세약국"), 토큰 접두 매칭만으로는
 * "약국" 같은 부분검색이 안 된다. 그래서 색인 텍스트를 **글자 단위 유니그램**으로 분해해 넣고
 * 검색어도 유니그램 **구문(phrase)** 으로 바꿔, 기존 `LIKE '%검색어%'` 와 동일한 부분검색을 유지한다.
 *   예) "100세약국" → 색인 "1 0 0 세 약 국",  검색 "약국" → MATCH `"약 국"` → 매칭.
 *
 * `places_fts` 는 Room 엔티티가 아니라 [MIGRATION_2_3] 이 만드는 비-엔티티 테이블이다
 * (docid == places.rowid). 검색 쿼리는 PlaceDao.searchByFts(RawQuery)로 실행한다.
 */
object PlaceFts {
    const val TABLE = "places_fts"

    fun create(db: SupportSQLiteDatabase) {
        // name / roadAddress 를 별도 컬럼으로 둔다 → 구문(phrase)이 필드 경계를 넘지 않는다.
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts4(name, roadAddress)")
    }

    /** places 전체를 다시 색인한다(첫 실행 마이그레이션 / 원격 갱신 후). */
    fun rebuild(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE")
            val stmt = db.compileStatement("INSERT INTO $TABLE(docid, name, roadAddress) VALUES(?, ?, ?)")
            db.query("SELECT rowid, name, roadAddress FROM places").use { c ->
                while (c.moveToNext()) {
                    stmt.clearBindings()
                    stmt.bindLong(1, c.getLong(0))
                    stmt.bindString(2, index(c.getString(1)))
                    stmt.bindString(3, index(c.getString(2)))
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 색인 텍스트: 한 필드의 글자(한글/영문/숫자)를 공백으로 분리한 유니그램. */
    fun index(field: String?): String = buildString {
        if (field == null) return@buildString
        for (ch in field) {
            if (ch.isLetterOrDigit()) {
                if (isNotEmpty()) append(' ')
                append(ch.lowercaseChar())
            }
        }
    }

    /**
     * 검색어를 FTS4 MATCH 식으로 변환. 공백으로 나눈 각 단어를 유니그램 구문으로 만들어 AND 결합한다.
     *   "강남 약국" → `"강 남" "약 국"`
     * 사용 가능한 글자가 없으면 null(→ 전체 조회로 폴백).
     */
    fun match(raw: String): String? {
        val phrases = raw.trim().split(Regex("\\s+")).mapNotNull { word ->
            val chars = word.filter { it.isLetterOrDigit() }.map { it.lowercaseChar() }
            if (chars.isEmpty()) null else chars.joinToString(" ", prefix = "\"", postfix = "\"")
        }
        return if (phrases.isEmpty()) null else phrases.joinToString(" ")
    }
}
