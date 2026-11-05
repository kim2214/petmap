package com.kimdev.petmap.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * 이름/주소 검색용 FTS4 색인(`places_fts`) 관리.
 *
 * 한국어는 상호가 공백 없이 붙는 경우가 많아("100세약국"), 토큰 접두 매칭만으로는
 * "약국" 같은 부분검색이 안 된다. 그래서 색인 텍스트를 **글자 단위 유니그램**으로 분해해 넣고
 * 검색어도 유니그램 **구문(phrase)** 으로 바꿔, 기존 `LIKE '%검색어%'` 와 동일한 부분검색을 유지한다.
 *   예) "100세약국" → 색인 "1 0 0 세 약 국",  검색 "약국" → MATCH `"약 국"` → 매칭.
 *
 * `places_fts` 는 Room 엔티티가 아닌 비-엔티티 테이블이다(docid == places.rowid).
 * 색인은 에셋 DB 와 원격 갱신 파일에 동봉되어 오며(tools/build_db.py 의 fts_index/build_fts), 앱은 검색어만
 * 같은 규칙으로 변환한다. 검색은 PlaceDao.searchByFts(RawQuery)로 실행한다.
 *
 * **색인 글자 규칙은 build_db.py 의 `str.isalnum()` 과 같아야 한다** — 유니코드 문자(L*)·십진숫자(Nd)·
 * 문자형 숫자(Nl, 로마숫자 Ⅱ)·기타 숫자(No, ①·½·²). Kotlin 의 isLetterOrDigit 은 Nl/No 를 빼서
 * "만의골보리밥Ⅱ" 같은 이름에서 색인(ⅱ 포함)과 검색어(ⅱ 제외)가 어긋났다. 소문자화도 파이썬 lower() 처럼
 * 코드포인트 단위 문자열 변환을 쓴다. 두 구현은 tools/fts_golden.json 을 공유하는 테스트로 고정한다.
 */
object PlaceFts {
    const val TABLE = "places_fts"

    fun create(db: SupportSQLiteDatabase) {
        // name / roadAddress 를 별도 컬럼으로 둔다 → 구문(phrase)이 필드 경계를 넘지 않는다.
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts4(name, roadAddress)")
    }

    /** places 전체를 다시 색인한다. 앱 런타임에선 쓰지 않는다 — 계측 테스트 픽스처(PlaceTestData)용. */
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

    /** 색인 텍스트: 한 필드의 글자(문자·숫자)를 공백으로 분리한 유니그램. */
    fun index(field: String?): String =
        field?.let { unigrams(it).joinToString(" ") }.orEmpty()

    /** 색인 대상 글자를 코드포인트 단위로 소문자화해 나열한다 (보조 평면 한자도 한 글자로 센다) */
    private fun unigrams(text: String): List<String> = buildList {
        text.codePoints().forEach { cp ->
            if (isIndexable(cp)) add(String(Character.toChars(cp)).lowercase(Locale.ROOT))
        }
    }

    /** 파이썬 str.isalnum() 과 같은 판정 (L*·Nd·Nl·No) */
    private fun isIndexable(cp: Int): Boolean = Character.isLetter(cp) || when (Character.getType(cp)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
        Character.LETTER_NUMBER.toInt(),
        Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    /**
     * 검색어를 FTS4 MATCH 식으로 변환. 공백으로 나눈 각 단어를 유니그램 구문으로 만들어 AND 결합한다.
     *   "강남 약국" → `"강 남" "약 국"`
     * 사용 가능한 글자가 없으면 null(→ 전체 조회로 폴백).
     */
    fun match(raw: String): String? {
        val phrases = raw.trim().split(Regex("\\s+")).mapNotNull { word ->
            val chars = unigrams(word)
            if (chars.isEmpty()) null else chars.joinToString(" ", prefix = "\"", postfix = "\"")
        }
        return if (phrases.isEmpty()) null else phrases.joinToString(" ")
    }
}
