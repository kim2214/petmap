package com.kimdev.petmap.data.local

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaceFtsTest {

    @Test
    fun `index splits fields into space-separated unigrams`() {
        assertEquals("1 0 0 세 약 국", PlaceFts.index("100세약국"))
    }

    @Test
    fun `index drops punctuation and whitespace`() {
        assertEquals("서 울", PlaceFts.index("서울!"))
        assertEquals("동 물 병 원", PlaceFts.index("동물 병원"))
    }

    @Test
    fun `index lowercases ASCII`() {
        assertEquals("c u", PlaceFts.index("CU"))
    }

    @Test
    fun `index returns empty for null`() {
        assertEquals("", PlaceFts.index(null))
    }

    @Test
    fun `match builds a quoted unigram phrase for one word`() {
        assertEquals("\"약 국\"", PlaceFts.match("약국"))
    }

    @Test
    fun `match ANDs multiple words as separate phrases`() {
        assertEquals("\"강 남\" \"약 국\"", PlaceFts.match("강남 약국"))
    }

    @Test
    fun `match strips punctuation inside a word`() {
        assertEquals("\"스 타 벅 스\"", PlaceFts.match("스타-벅스"))
    }

    @Test
    fun `match returns null when no usable characters`() {
        assertNull(PlaceFts.match("   "))
        assertNull(PlaceFts.match("!!!"))
        assertNull(PlaceFts.match(""))
    }

    /**
     * build_db.py 의 fts_index 와 같은 골든을 공유한다(tools/fts_golden.json, test_build_db.FtsGoldenTest).
     * 에셋·원격 갱신 DB 의 색인은 파이썬이 만들고 검색어는 앱이 변환하므로, 둘이 갈리면 검색이 조용히 빗나간다.
     */
    @Test
    fun `index and match agree with build_db golden`() {
        // 단위 테스트 작업 디렉터리는 모듈(app/)이지만 IDE 실행 설정에 따라 루트일 수도 있다
        val golden = listOf(File("../tools/fts_golden.json"), File("tools/fts_golden.json")).first { it.exists() }
        val cases = JSONObject(golden.readText()).getJSONArray("cases")
        assertTrue(cases.length() > 5)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val input = c.getString("input")
            val expected = c.getString("index")
            assertEquals(c.getString("note"), expected, PlaceFts.index(input))
            // 검색어 변환도 같은 글자 규칙이어야 한다 (단어 하나짜리 입력은 색인과 같은 유니그램 구문)
            if (expected.isNotEmpty() && input.none { it.isWhitespace() }) {
                assertEquals(c.getString("note"), "\"$expected\"", PlaceFts.match(input))
            }
        }
    }
}
