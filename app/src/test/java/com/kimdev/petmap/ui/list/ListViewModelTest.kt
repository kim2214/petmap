package com.kimdev.petmap.ui.list

import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.fake.FakeLocationProvider
import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakeRecentSearchStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePlaceRepository()

    private fun viewModel(location: UserLocation? = null): ListViewModel =
        ListViewModel(
            repo,
            FakeLocationProvider(location),
            FakeRecentSearchStore(),
            mainDispatcherRule.testDispatcher,
        )

    @Test
    fun `초기화 시 시딩하고 위치 없으면 hasLocation false`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(location = null)
        advanceUntilIdle()

        assertEquals(1, repo.ensureSeededCount)
        assertFalse(vm.uiState.value.hasLocation)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `위치 제공되면 hasLocation true`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(location = UserLocation(37.5, 127.0))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasLocation)
    }

    @Test
    fun `기본 검색은 search 를 limit 200 으로 호출`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a"), testPlace("b"))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(repo.searchCount >= 1)
        assertEquals(0, repo.searchNearbyCount)
        assertEquals(200, repo.lastSearchLimit)
        assertEquals(2, vm.uiState.value.places.size)
    }

    @Test
    fun `거리순 정렬이고 위치 있으면 searchNearby 사용`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a"))
        val vm = viewModel(location = UserLocation(37.5, 127.0))
        advanceUntilIdle()

        vm.setSortByDistance(true)
        advanceUntilIdle()

        assertTrue(repo.searchNearbyCount >= 1)
    }

    @Test
    fun `거리순 정렬이어도 위치 없으면 search 로 폴백`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a"))
        val vm = viewModel(location = null)
        advanceUntilIdle()

        vm.setSortByDistance(true)
        advanceUntilIdle()

        assertEquals(0, repo.searchNearbyCount)
        assertTrue(repo.searchCount >= 1)
    }

    @Test
    fun `영업중 필터는 limit 400 으로 가져오고 영업중만 남긴다`() = runTest(mainDispatcherRule.testDispatcher) {
        // "24시간"=항상 영업, 시간정보 없음=판단불가(null)→제외
        repo.dataset = listOf(
            testPlace("open", operatingTime = "24시간"),
            testPlace("unknown", operatingTime = null),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.setOpenNowOnly(true)
        advanceUntilIdle()

        assertEquals(400, repo.lastSearchLimit)
        assertEquals(listOf("open"), vm.uiState.value.places.map { it.id })
    }

    @Test
    fun `카테고리 토글이 선택 상태와 재검색에 반영된다`() = runTest(mainDispatcherRule.testDispatcher) {
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
        assertEquals(listOf("cafe"), vm.uiState.value.places.map { it.id })

        // 다시 토글하면 해제
        vm.toggleCategory(PlaceCategory.CAFE)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedCategories.isEmpty())
    }

    @Test
    fun `결과는 200개로 잘린다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = (1..250).map { testPlace("p$it") }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(200, vm.uiState.value.places.size)
    }

    @Test
    fun `즐겨찾기 토글이 목록의 isFavorite 에 반영된다`() = runTest(mainDispatcherRule.testDispatcher) {
        val place = testPlace("a")
        repo.dataset = listOf(place)
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.places.first().isFavorite)

        vm.toggleFavorite(place)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.places.first().isFavorite)
    }
}
