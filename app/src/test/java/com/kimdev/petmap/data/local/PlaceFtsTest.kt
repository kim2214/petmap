package com.kimdev.petmap.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
