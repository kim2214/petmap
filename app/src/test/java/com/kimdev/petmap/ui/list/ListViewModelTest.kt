package com.kimdev.petmap.ui.list

import androidx.lifecycle.SavedStateHandle
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

    private fun viewModel(
        location: UserLocation? = null,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): ListViewModel =
        ListViewModel(
            repo,
            FakeLocationProvider(location),
            FakeRecentSearchStore(),
            mainDispatcherRule.testDispatcher,
            savedState,
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
    fun `검색어와 필터가 저장되어 새 인스턴스에서 복원된다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("cafe", name = "행복카페", category = PlaceCategory.CAFE))
        val saved = SavedStateHandle()
        val vm = viewModel(savedState = saved)
        advanceUntilIdle()

        vm.onQueryChange("카페")
        vm.toggleCategory(PlaceCategory.CAFE)
        vm.setSortByDistance(true)
        vm.setOpenNowOnly(true)
        advanceUntilIdle()

        // 같은 SavedStateHandle 로 재생성(프로세스 사망 후 복원 시나리오)
        val restored = viewModel(savedState = saved)
        advanceUntilIdle()
        val s = restored.uiState.value
        assertEquals("카페", s.query)
        assertEquals(setOf(PlaceCategory.CAFE), s.selectedCategories)
        assertTrue(s.sortByDistance)
        assertTrue(s.openNowOnly)
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

    @Test
    fun `첫 페이지는 200건이고 더 있으면 canLoadMore true`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = (1..250).map { testPlace("p$it") }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(200, repo.lastSearchLimit)
        assertEquals(200, vm.uiState.value.places.size)
        assertTrue(vm.uiState.value.canLoadMore)
        assertFalse(vm.uiState.value.reachedLimit)
    }

    @Test
    fun `결과가 페이지보다 적으면 canLoadMore false`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = (1..20).map { testPlace("p$it") }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(20, vm.uiState.value.places.size)
        assertFalse(vm.uiState.value.canLoadMore)
    }

    @Test
    fun `loadMore 는 다음 페이지를 이어 붙인다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = (1..450).map { testPlace("p$it") }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(200, vm.uiState.value.places.size)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(400, repo.lastSearchLimit)
        assertEquals(400, vm.uiState.value.places.size)
        assertTrue(vm.uiState.value.canLoadMore)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `검색 조건이 바뀌면 페이지가 첫 페이지로 초기화된다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = (1..450).map { testPlace("p$it", name = "카페$it") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(400, repo.lastSearchLimit)

        vm.onQueryChange("카페")
        advanceUntilIdle()
        assertEquals(200, repo.lastSearchLimit)
        assertEquals(200, vm.uiState.value.places.size)
    }

    @Test
    fun `상한에 닿으면 canLoadMore 대신 reachedLimit 을 알린다`() = runTest(mainDispatcherRule.testDispatcher) {
        // 상한(2000)을 넘는 데이터: 10회 loadMore 후에는 더 불러오지 않고 안내로 전환
        repo.dataset = (1..2500).map { testPlace("p$it") }
        val vm = viewModel()
        advanceUntilIdle()
        repeat(20) {
            vm.loadMore()
            advanceUntilIdle()
        }

        assertEquals(2000, repo.lastSearchLimit)
        assertEquals(2000, vm.uiState.value.places.size)
        assertFalse(vm.uiState.value.canLoadMore)
        assertTrue(vm.uiState.value.reachedLimit)
    }
}
