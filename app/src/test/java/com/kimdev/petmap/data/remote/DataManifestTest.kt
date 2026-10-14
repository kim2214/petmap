package com.kimdev.petmap.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DataManifestTest {

    private val sha = "a".repeat(64)

    private fun json(
        dataDate: String = "2026-10-14",
        file: String = "petmap-2026-10-14.db.gz",
        sha256: String = sha,
        size: Long = 5_321_112,
        placeCount: Int = 24_700,
    ) = """{"dataDate":"$dataDate","file":"$file","sha256":"$sha256","size":$size,"placeCount":$placeCount}"""

    @Test
    fun parse_validManifest() {
        val m = DataManifest.parse(json())
        assertEquals(LocalDate.of(2026, 10, 14), m.dataDate)
        assertEquals("petmap-2026-10-14.db.gz", m.file)
        assertEquals(sha, m.sha256)
        assertEquals(5_321_112L, m.size)
        assertEquals(24_700, m.placeCount)
    }

    @Test
    fun parse_uppercaseShaIsNormalized() {
        assertEquals(sha, DataManifest.parse(json(sha256 = "A".repeat(64))).sha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejectsBrokenJson() {
        DataManifest.parse("<html>404</html>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejectsMissingField() {
        DataManifest.parse("""{"dataDate":"2026-10-14"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejectsBadDate() {
        DataManifest.parse(json(dataDate = "2026/10/14"))
    }

    @Test
    fun parse_rejectsPathTraversalAndUrls() {
        listOf("../petmap.db.gz", "a/b.db.gz", "https:x", "..").forEach { file ->
            val result = runCatching { DataManifest.parse(json(file = file)) }
            assertTrue("허용되면 안 됨: $file", result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun parse_rejectsBadShaAndSizes() {
        listOf(
            json(sha256 = "abc"),
            json(size = 0),
            json(size = DataManifest.MAX_SIZE + 1),
            json(placeCount = 0),
        ).forEach { raw ->
            assertTrue(raw, runCatching { DataManifest.parse(raw) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun isNewerThan_comparesDataDate() {
        val m = DataManifest.parse(json(dataDate = "2026-10-14"))
        assertTrue(m.isNewerThan(LocalDate.of(2026, 9, 10)))
        assertFalse(m.isNewerThan(LocalDate.of(2026, 10, 14)))
        assertFalse(m.isNewerThan(LocalDate.of(2026, 11, 1)))
        // 기준일을 모르면(meta 없음) 받는다
        assertTrue(m.isNewerThan(null))
    }
}
