package com.kimdev.petmap.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakeRecentPlaceStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.ui.navigation.Routes
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePlaceRepository().apply { dataset = listOf(testPlace("a")) }
    private val recent = FakeRecentPlaceStore()

    private fun viewModel(id: String) =
        DetailViewModel(repo, recent, SavedStateHandle(mapOf(Routes.DETAIL_ARG_ID to id)))

    @Test
    fun `장소를 열면 최근 본 장소 맨 앞에 남는다`() = runTest(mainDispatcherRule.testDispatcher) {
        recent.add("x")
        viewModel("a")
        advanceUntilIdle()
        assertEquals(listOf("a", "x"), recent.recent.value)
    }

    @Test
    fun `옛 공유 링크로 열면 하트·메모·최근 본 장소는 지금 id 로 맞춘다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.legacyAliases["(주)a_37.5_127.0"] = "a"
        repo.setFavorites(setOf("a"))
        val vm = viewModel("(주)a_37.5_127.0")
        advanceUntilIdle()

        assertEquals("a", vm.uiState.value.place?.id)
        assertTrue(vm.uiState.value.place!!.isFavorite)
        assertEquals(listOf("a"), recent.recent.value)

        vm.saveMemo("메모")
        advanceUntilIdle()
        assertEquals("메모", repo.memos["a"])
    }

    @Test
    fun `없는 장소는 최근 본 장소에 남기지 않는다`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel("missing")
        advanceUntilIdle()
        assertTrue(recent.recent.value.isEmpty())
    }

    @Test
    fun `원격 갱신으로 데이터가 바뀌면 열린 상세도 새 정보로 바뀐다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel("a")
        advanceUntilIdle()
        assertEquals("a", vm.uiState.value.place?.name)

        repo.dataset = listOf(testPlace("a", name = "새 이름"))
        repo.simulateDataReplaced()
        advanceUntilIdle()
        assertEquals("새 이름", vm.uiState.value.place?.name)
    }

    @Test
    fun `갱신으로 장소가 빠져도 보고 있던 정보는 남는다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel("a")
        advanceUntilIdle()

        repo.dataset = emptyList()
        repo.simulateDataReplaced()
        advanceUntilIdle()
        assertEquals("a", vm.uiState.value.place?.id)
        assertEquals(listOf("a"), recent.recent.value)
    }
}
