package com.kimdev.petmap.ui.map

import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.common.MapFocusBus
import androidx.lifecycle.SavedStateHandle
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
        MapViewModel(repo, focusBus, FakeRecentSearchStore(), FakeLocationProvider(), mainDispatcherRule.testDispatcher, savedState)

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
        assertEquals(2, state.clusters.sumOf { it.count })
    }

    @Test
    fun `고줌에서는 개별 조회 경로를 쓴다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a", lat = 37.50, lng = 127.00))
        val vm = viewModel()
        advanceUntilIdle()

        vm.researchHere(37.5, 127.0, 16.0)
        advanceUntilIdle()

        assertEquals(2000, repo.lastBoundsLimit)
        // 개별 좌표를 갖고 있어야 로컬 재클러스터링·클러스터 펼치기가 가능하다
        assertEquals(listOf("a"), vm.uiState.value.clusters.mapNotNull { it.single?.id })
    }

    @Test
    fun `개별 조회가 한도에 걸리면 집계 경로로 폴백한다`() = runTest(mainDispatcherRule.testDispatcher) {
        // 한도(2000)만큼 채우면 DAO 가 중심 거리순으로 잘랐다는 뜻 → 그대로 그리면
        // 화면 가장자리가 비고 개수도 실제보다 작아지므로 집계 경로로 전환해야 한다.
        repo.dataset = (1..2000).map { testPlace("p$it", lat = 37.5 + it * 1e-5, lng = 127.0) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.researchHere(37.5, 127.0, 16.0) // 고줌 = 개별 경로 진입
        advanceUntilIdle()

        assertEquals(2000, repo.lastBoundsLimit)          // 개별 조회를 먼저 시도했고
        assertEquals(225, repo.lastClusterCellsLimit)     // 한도에 걸려 집계로 폴백했다
        val clusters = vm.uiState.value.clusters
        assertTrue(clusters.isNotEmpty())
        assertTrue("집계 클러스터는 개별 좌표가 없다", clusters.all { it.members.isEmpty() })
    }

    @Test
    fun `줌 버킷이 바뀌면 자동으로 재조회한다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a", lat = 37.50, lng = 127.00))
        val vm = viewModel()
        advanceUntilIdle()
        // 기본 줌 12 = 집계 경로라 개별 조회는 아직 없었다
        assertNull(repo.lastBoundsLimit)

        // 같은 지점에서 줌만 12 → 16 (조회 반경 12km → 1.5km): 격자 크기가 달라져
        // 지금 표시된 개수가 의미를 잃으므로 버튼을 기다리지 않고 다시 조회한다.
        vm.onCameraMove(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG, 16.0)
        advanceUntilIdle()

        assertEquals(2000, repo.lastBoundsLimit)
        assertFalse(vm.uiState.value.canResearch)
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
    fun `저줌에서는 그리드 집계 경로로 클러스터를 채운다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(
            testPlace("a", lat = 37.50, lng = 127.00),
            testPlace("b", lat = 37.90, lng = 127.40),
        )
        val vm = viewModel()
        advanceUntilIdle()

        // 광역(줌 8)으로 재검색 → 개별 조회가 아니라 getClusterCells 사용
        vm.researchHere(37.5, 127.0, 8.0)
        advanceUntilIdle()

        // 한도는 격자 수(15²)와 같아야 한다. 더 작으면 어떤 셀이 버려지는지 정의되지 않아
        // 특정 지역이 통째로 빈 것처럼 보인다.
        assertEquals(225, repo.lastClusterCellsLimit)
        assertEquals(2, vm.uiState.value.clusters.sumOf { it.count })
        // 집계 클러스터는 개별 좌표를 갖지 않으므로 members 는 비어 있다
        assertTrue(vm.uiState.value.clusters.all { it.members.isEmpty() })
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
