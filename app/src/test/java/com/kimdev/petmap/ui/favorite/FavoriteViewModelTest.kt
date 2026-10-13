package com.kimdev.petmap.ui.favorite

import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakeRecentPlaceStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePlaceRepository().apply {
        dataset = listOf(testPlace("a"), testPlace("b"), testPlace("c"))
    }

    @Test
    fun `최근 본 장소는 최신 우선이고 사라진 장소는 빠진다`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeRecentPlaceStore(listOf("c", "gone", "a"))
        val vm = FavoriteViewModel(repo, store)
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()

        assertFalse(vm.recentState.value.isLoading)
        assertEquals(listOf("c", "a"), vm.recentState.value.places.map { it.id })

        store.add("a") // 다시 보면 맨 앞으로
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), vm.recentState.value.places.map { it.id })
    }

    @Test
    fun `최근 본 목록의 하트는 즐겨찾기와 동기화되고 토글해도 카드는 남는다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = FavoriteViewModel(repo, FakeRecentPlaceStore(listOf("b")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()
        assertFalse(vm.recentState.value.places.single().isFavorite)

        vm.toggleFavorite(vm.recentState.value.places.single())
        advanceUntilIdle()
        assertTrue(vm.recentState.value.places.single().isFavorite)
    }

    @Test
    fun `clearRecent 는 목록을 비운다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = FavoriteViewModel(repo, FakeRecentPlaceStore(listOf("a", "b")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()

        vm.clearRecent()
        advanceUntilIdle()
        assertTrue(vm.recentState.value.places.isEmpty())
    }
}
