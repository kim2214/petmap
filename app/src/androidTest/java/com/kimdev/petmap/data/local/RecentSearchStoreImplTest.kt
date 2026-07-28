package com.kimdev.petmap.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RecentSearchStoreImpl 실동작 테스트.
 * 유닛 테스트는 FakeRecentSearchStore 를 쓰므로 실제 구현(직렬화·개수 제한·중복 제거·순서)은
 * 여기서만 검증된다.
 */
@RunWith(AndroidJUnit4::class)
class RecentSearchStoreImplTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 이전 테스트 잔여 데이터 제거
        context.getSharedPreferences("recent_search", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newStore() = RecentSearchStoreImpl(context)

    /** 저장 결과가 새 인스턴스에 반영될 때까지 기다린다(반환값 없음 — JUnit4 는 void 메서드를 요구). */
    private suspend fun RecentSearchStoreImpl.awaitRecent(expected: List<String>) {
        withTimeout(TIMEOUT_MS) { recent.first { it == expected } }
    }

    @Test
    fun addKeepsNewestFirstAndDeduplicates() {
        val store = newStore()
        store.add("카페")
        store.add("병원")
        store.add("카페") // 중복 → 맨 앞으로 이동, 중복 항목 없음

        assertEquals(listOf("카페", "병원"), store.recent.value)
    }

    @Test
    fun addTrimsAndIgnoresBlank() {
        val store = newStore()
        store.add("  카페  ")
        store.add("   ")
        store.add("")

        assertEquals(listOf("카페"), store.recent.value)
    }

    @Test
    fun keepsAtMostEightEntries() {
        val store = newStore()
        (1..12).forEach { store.add("검색$it") }

        assertEquals(8, store.recent.value.size)
        assertEquals("검색12", store.recent.value.first()) // 최신 우선
        assertTrue(store.recent.value.none { it == "검색1" }) // 오래된 항목은 밀려남
    }

    @Test
    fun persistsAcrossInstances_preservingOrder() = runBlocking {
        val first = newStore()
        first.add("첫번째")
        first.add("두번째")
        first.add("세번째")
        // 디스크 쓰기가 끝난 뒤 새 인스턴스가 같은 순서로 읽어야 한다
        newStore().awaitRecent(listOf("세번째", "두번째", "첫번째"))
    }

    @Test
    fun persistsQueryContainingNewline() = runBlocking {
        // 이전 구현은 "\n" join 이라 개행이 들어가면 항목이 쪼개졌다
        val store = newStore()
        store.add("줄바꿈\n검색어")

        newStore().awaitRecent(listOf("줄바꿈\n검색어"))
    }

    @Test
    fun removeAndClear() = runBlocking {
        val store = newStore()
        store.add("a")
        store.add("b")

        store.remove("a")
        assertEquals(listOf("b"), store.recent.value)

        store.clear()
        assertEquals(emptyList<String>(), store.recent.value)
        newStore().awaitRecent(emptyList())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
