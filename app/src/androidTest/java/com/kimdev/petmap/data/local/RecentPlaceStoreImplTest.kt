package com.kimdev.petmap.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** RecentPlaceStoreImpl 실동작 테스트 (유닛 테스트는 FakeRecentPlaceStore 를 쓴다). */
@RunWith(AndroidJUnit4::class)
class RecentPlaceStoreImplTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("recent_place", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newStore() = RecentPlaceStoreImpl(context)

    private suspend fun RecentPlaceStoreImpl.awaitRecent(expected: List<String>) {
        withTimeout(5_000L) { recent.first { it == expected } }
    }

    @Test
    fun addKeepsNewestFirst_dedupes_andCapsAtMax() {
        val store = newStore()
        (1..RecentPlaceStore.MAX + 5).forEach { store.add("p$it") }
        store.add("p10") // 다시 보면 맨 앞으로

        assertEquals(RecentPlaceStore.MAX, store.recent.value.size)
        assertEquals(listOf("p10", "p${RecentPlaceStore.MAX + 5}"), store.recent.value.take(2))
        assertEquals(1, store.recent.value.count { it == "p10" })
    }

    @Test
    fun persistsAcrossInstances_preservingOrder() = runBlocking {
        val store = newStore()
        store.add("a")
        store.add("b")
        store.add("c")
        newStore().awaitRecent(listOf("c", "b", "a"))
    }

    @Test
    fun clearPersists() = runBlocking {
        val store = newStore()
        store.add("a")
        store.clear()
        newStore().awaitRecent(emptyList())
    }
}
