package com.kimdev.petmap.ui.map

import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.MapFocusBus
import androidx.lifecycle.SavedStateHandle
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakeRecentSearchStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePlaceRepository()
    private val focusBus = MapFocusBus()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): MapViewModel =
        MapViewModel(repo, focusBus, FakeRecentSearchStore(), mainDispatcherRule.testDispatcher, savedState)

    @Test
    fun `초기화 후 시딩 완료되고 클러스터가 채워진다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(
            testPlace("a", lat = 37.50, lng = 127.00),
            testPlace("b", lat = 37.60, lng = 127.10),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, repo.ensureSeededCount)
        assertFalse(state.isSeeding)
        assertFalse(state.isLoading)
        assertFalse(state.canResearch)
        assertEquals(500, repo.lastBoundsLimit)
        assertEquals(2, state.clusters.sumOf { it.count })
    }

    @Test
    fun `검색어 입력 시 디바운스 후 결과가 채워진다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("cafe1", name = "행복카페"), testPlace("hosp", name = "튼튼병원"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("카페")
        advanceUntilIdle()

        assertEquals(listOf("cafe1"), vm.uiState.value.searchResults.map { it.id })
    }

    @Test
    fun `검색어를 비우면 결과도 비워진다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("cafe1", name = "행복카페"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("카페")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.searchResults.isNotEmpty())

        vm.clearSearch()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.searchResults.isEmpty())
        assertEquals("", vm.uiState.value.searchQuery)
    }

    @Test
    fun `카테고리 토글 시 재조회하고 선택 상태가 반영된다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(
            testPlace("cafe", category = PlaceCategory.CAFE),
            testPlace("hosp", category = PlaceCategory.HOSPITAL),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleCategory(PlaceCategory.CAFE)
        advanceUntilIdle()

        assertEquals(setOf(PlaceCategory.CAFE), vm.uiState.value.selectedCategories)
        assertEquals(setOf(PlaceCategory.CAFE), repo.lastCategories)
        assertEquals(1, vm.uiState.value.clusters.sumOf { it.count })
    }

    @Test
    fun `카테고리 필터가 저장되어 새 인스턴스에서 복원된다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("cafe", category = PlaceCategory.CAFE))
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()
        vm.toggleCategory(PlaceCategory.CAFE)
        advanceUntilIdle()

        // 같은 SavedStateHandle 로 재생성(프로세스 사망 후 복원 시나리오)
        val restored = viewModel(saved)
        advanceUntilIdle()
        assertEquals(setOf(PlaceCategory.CAFE), restored.uiState.value.selectedCategories)
        assertEquals(setOf(PlaceCategory.CAFE), repo.lastCategories) // 복원된 필터로 조회
    }

    @Test
    fun `MapFocusBus 요청 시 해당 장소를 포커스 대상으로 잡고 소비하면 해제된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("target"))
            val vm = viewModel()
            advanceUntilIdle()

            focusBus.request("target")
            advanceUntilIdle()
            assertEquals("target", vm.uiState.value.focusTarget?.id)

            vm.consumeFocus()
            advanceUntilIdle()
            assertNull(vm.uiState.value.focusTarget)
        }

    @Test
    fun `멀리 이동하면 canResearch true, 다시 검색하면 false`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a"))
        val vm = viewModel()
        advanceUntilIdle()

        // 기본 조회 지점(서울시청)에서 약 11km 북쪽으로 이동 → 재검색 필요
        vm.onCameraMove(Constants.DEFAULT_LAT + 0.1, Constants.DEFAULT_LNG, Constants.DEFAULT_ZOOM)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canResearch)

        vm.researchHere(Constants.DEFAULT_LAT + 0.1, Constants.DEFAULT_LNG, Constants.DEFAULT_ZOOM)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canResearch)
    }
}
