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
    fun `없는 장소는 최근 본 장소에 남기지 않는다`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel("missing")
        advanceUntilIdle()
        assertTrue(recent.recent.value.isEmpty())
    }
}
