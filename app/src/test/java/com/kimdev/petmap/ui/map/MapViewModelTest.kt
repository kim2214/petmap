package com.kimdev.petmap.ui.map

import com.kimdev.petmap.core.common.Constants
import androidx.lifecycle.SavedStateHandle
import com.kimdev.petmap.domain.model.PetFilter
import com.kimdev.petmap.domain.model.Region
import com.kimdev.petmap.domain.model.GeoBounds
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.ui.navigation.Routes
import com.kimdev.petmap.fake.FakeLocationProvider
import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakePetProfileStore
import com.kimdev.petmap.fake.FakeRecentSearchStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
    private val petProfileStore = FakePetProfileStore()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): MapViewModel =
        MapViewModel(repo, FakeRecentSearchStore(), petProfileStore, FakeLocationProvider(), mainDispatcherRule.testDispatcher, savedState)

    @Test
    fun `지역을 고르면 그 지역 장소 범위로 FitRegion 을 보내고 없으면 RegionEmpty`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(
            testPlace("g1", roadAddress = "경기도 고양시 덕양구 1", lat = 37.60, lng = 126.80),
            testPlace("g2", roadAddress = "경기도 고양시 일산서구 2", lat = 37.70, lng = 126.90),
            testPlace("s1", roadAddress = "서울특별시 강남구 3", lat = 37.50, lng = 127.05),
        )
        val vm = viewModel()
        advanceUntilIdle()
        val received = mutableListOf<MapEvent>()
        val job = launch { vm.events.toList(received) }

        vm.selectRegion(Region("경기도", "고양시"))
        advanceUntilIdle()
        val fit = received.filterIsInstance<MapEvent.FitRegion>().single()
        assertEquals(GeoBounds(37.60, 37.70, 126.80, 126.90), fit.bounds)

        vm.selectRegion(Region("제주특별자치도"))
        advanceUntilIdle()
        assertEquals(Region("제주특별자치도"), received.filterIsInstance<MapEvent.RegionEmpty>().single().region)

        vm.loadRegionCounts(); advanceUntilIdle()
        assertEquals(setOf("경기도", "서울특별시"), vm.uiState.value.regionCounts.map { it.sido }.toSet())
        job.cancel()
    }

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
    fun `시딩 실패 시 seedFailed 로 드러내고 조회를 시작하지 않는다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a", lat = 37.50, lng = 127.00))
        repo.seedFailure = IllegalStateException("asset copy failed")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSeeding)
        assertTrue(state.seedFailed)
        assertTrue(state.clusters.isEmpty())

        // 실패 상태에서는 카메라 이동/재검색도 DB 를 건드리지 않는다
        vm.researchHere(37.5, 127.0, 16.0)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.clusters.isEmpty())
    }

    @Test
    fun `시딩 재시도가 성공하면 정상 진입한다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("a", lat = 37.50, lng = 127.00))
        repo.seedFailure = IllegalStateException("asset copy failed")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.seedFailed)

        repo.seedFailure = null
        vm.retrySeeding()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, repo.ensureSeededCount)
        assertFalse(state.seedFailed)
        assertFalse(state.isSeeding)
        assertEquals(1, state.clusters.sumOf { it.count })
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
    fun `원격 갱신으로 데이터가 바뀌면 지금 보는 영역을 다시 조회한다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("old", lat = 37.50, lng = 127.00))
        val vm = viewModel()
        advanceUntilIdle()
        vm.researchHere(37.5, 127.0, 16.0)
        advanceUntilIdle()
        assertEquals(listOf("old"), vm.uiState.value.clusters.mapNotNull { it.single?.id })

        repo.dataset = listOf(testPlace("new", lat = 37.50, lng = 127.00))
        repo.simulateDataReplaced()
        advanceUntilIdle()

        assertEquals(listOf("new"), vm.uiState.value.clusters.mapNotNull { it.single?.id })
    }

    @Test
    fun `원격 갱신 후 열린 미리보기·클러스터 목록은 새 데이터로 바뀌고 빠진 장소는 사라진다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("a"), testPlace("b"), testPlace("c"))
            val vm = viewModel()
            advanceUntilIdle()
            vm.showPreview(repo.dataset[0])
            vm.showClusterList(repo.dataset)

            repo.dataset = listOf(testPlace("a", name = "새 이름"), testPlace("c"))
            repo.simulateDataReplaced()
            advanceUntilIdle()
            assertEquals("새 이름", vm.previewPlace.value?.name)
            assertEquals(listOf("a", "c"), vm.clusterList.value?.map { it.id })

            repo.dataset = emptyList()
            repo.simulateDataReplaced()
            advanceUntilIdle()
            assertNull(vm.previewPlace.value)
            assertNull(vm.clusterList.value)
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
    fun `focus 라우트 인자가 있으면 시딩 후 FocusPlace 이벤트를 한 번 보내고 그 위치로 조회한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("target", lat = 35.10, lng = 129.00), testPlace("far", lat = 37.5, lng = 127.0))
            val vm = viewModel(SavedStateHandle(mapOf(Routes.MAP_ARG_FOCUS to "target")))
            advanceUntilIdle()

            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }
            advanceUntilIdle()

            val focus = received.filterIsInstance<MapEvent.FocusPlace>()
            assertEquals(1, focus.size)
            assertEquals("target", focus.single().place.id)
            // 초기 현재 위치 이동은 포커스에 양보한다
            assertTrue(received.none { it is MapEvent.MoveToLocation })
            job.cancel()
        }

    @Test
    fun `focus 인자가 없으면 FocusPlace 이벤트를 보내지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("a"))
            val vm = viewModel()
            advanceUntilIdle()

            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }
            advanceUntilIdle()

            assertTrue(received.none { it is MapEvent.FocusPlace })
            job.cancel()
        }

    @Test
    fun `검색 확정은 결과 전체를 마커로 뿌리고 카메라 맞춤 이벤트를 보낸다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(
                testPlace("행복카페", lat = 37.50, lng = 127.00),
                testPlace("행복동물병원", lat = 35.10, lng = 129.00),
                testPlace("다른곳", lat = 37.51, lng = 127.01),
            )
            val vm = viewModel()
            advanceUntilIdle()
            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }

            vm.onSearchQueryChange("행복")
            vm.submitSearch("행복")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SearchMarkers("행복", 2, truncated = false), state.searchMarkers)
            assertEquals(2, state.clusters.sumOf { it.count })
            assertTrue(state.searchResults.isEmpty()) // 자동완성 목록은 내려간다
            assertEquals(2, (received.last() as MapEvent.FitPlaces).places.size)

            // 카메라를 크게 옮겨도 재조회하지 않고 "다시 검색"도 뜨지 않는다
            val boundsBefore = repo.lastBoundsLimit
            vm.onCameraMove(33.0, 126.0, 9.0)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canResearch)
            assertEquals(boundsBefore, repo.lastBoundsLimit)

            // 해제하면 뷰포트 조회로 복귀
            vm.clearSearch()
            advanceUntilIdle()
            assertNull(vm.uiState.value.searchMarkers)
            assertEquals(3, vm.uiState.value.clusters.sumOf { it.count })
            job.cancel()
        }

    @Test
    fun `검색 확정 진행 중에 clearSearch 하면 마커 모드에 들어가지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("행복카페", lat = 37.50, lng = 127.00))
            val vm = viewModel()
            advanceUntilIdle()
            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }

            val gate = CompletableDeferred<Unit>()
            repo.searchGate = gate
            vm.onSearchQueryChange("행복")
            vm.submitSearch("행복")
            advanceUntilIdle() // search 에서 대기 중
            assertTrue(vm.uiState.value.isLoading)

            vm.clearSearch() // ✕ 또는 뒤로가기
            gate.complete(Unit)
            repo.searchGate = null
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull("취소된 검색이 마커 모드를 켜면 빈 검색창 + pill 상태에 갇힌다", state.searchMarkers)
            assertEquals("", state.searchQuery)
            assertFalse(state.isLoading)
            assertTrue(received.none { it is MapEvent.FitPlaces })
            job.cancel()
        }

    @Test
    fun `마커 모드에서 검색어를 지우면 해제되고 뷰포트 조회로 복귀한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(
                testPlace("행복카페", lat = 37.50, lng = 127.00),
                testPlace("다른곳", lat = 37.51, lng = 127.01),
            )
            val vm = viewModel()
            advanceUntilIdle()
            vm.onSearchQueryChange("행복")
            vm.submitSearch("행복")
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.clusters.sumOf { it.count })

            vm.onSearchQueryChange("") // 백스페이스로 전부 지움
            advanceUntilIdle()

            assertNull(vm.uiState.value.searchMarkers)
            assertEquals(2, vm.uiState.value.clusters.sumOf { it.count })
        }

    @Test
    fun `검색 확정은 동반 조건을 SQL 로 넘기고 truncated 는 그 결과로 판단한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // 실내 가능 장소가 상한(200)+1 건: 필터를 조회 뒤에 걸었다면 앞 201건 중 일부만 남아 유실된다
            repo.dataset = (1..201).map { testPlace("카페$it", lat = 37.5 + it * 1e-4, lng = 127.0, indoorAllowed = true) } +
                (1..50).map { testPlace("실외카페$it", lat = 37.6, lng = 127.1, indoorAllowed = false) }
            val vm = viewModel()
            advanceUntilIdle()
            vm.togglePetFilter(PetFilter.INDOOR)
            advanceUntilIdle()

            vm.submitSearch("카페")
            advanceUntilIdle()

            assertEquals(setOf(PetFilter.INDOOR), repo.lastSearchPetFilters)
            val markers = vm.uiState.value.searchMarkers!!
            assertEquals(MapViewModel.SEARCH_MARKERS_LIMIT, markers.count)
            assertTrue("201건이 걸리면 '이상' 표기여야 한다", markers.truncated)
        }

    @Test
    fun `마커 모드에서 필터를 토글하면 결과는 갱신되지만 카메라는 맞추지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(
                testPlace("행복카페", lat = 37.50, lng = 127.00, indoorAllowed = true),
                testPlace("행복정원", lat = 35.10, lng = 129.00, indoorAllowed = false),
            )
            val vm = viewModel()
            advanceUntilIdle()
            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }
            vm.submitSearch("행복")
            advanceUntilIdle()
            assertEquals(1, received.count { it is MapEvent.FitPlaces })
            assertEquals(2, vm.uiState.value.searchMarkers?.count)

            vm.togglePetFilter(PetFilter.INDOOR) // 사용자가 줌인해 보던 곳에서 전국 뷰로 튀면 안 된다
            advanceUntilIdle()

            assertEquals(1, vm.uiState.value.searchMarkers?.count)
            assertEquals(1, received.count { it is MapEvent.FitPlaces })
            job.cancel()
        }

    @Test
    fun `검색 확정 결과가 0건이면 마커 모드에 들어가지 않고 기존 마커를 유지한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = listOf(testPlace("a", lat = 37.50, lng = 127.00))
            val vm = viewModel()
            advanceUntilIdle()

            vm.submitSearch("없는이름")
            advanceUntilIdle()

            assertNull(vm.uiState.value.searchMarkers)
            assertEquals(1, vm.uiState.value.clusters.sumOf { it.count })
        }

    @Test
    fun `조회 결과가 0건이면 NoResults 이벤트를 보낸다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repo.dataset = emptyList()
            val vm = viewModel()
            advanceUntilIdle()

            val received = mutableListOf<MapEvent>()
            val job = launch { vm.events.toList(received) }
            vm.researchHere(37.5, 127.0, 16.0)
            advanceUntilIdle()

            assertTrue(received.contains(MapEvent.NoResults))
            job.cancel()
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
    fun `집계 클러스터 탭이 셀 범위 재조회로 목록 이벤트를 보낸다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(
            testPlace("in1", lat = 37.50, lng = 127.00),
            testPlace("in2", lat = 37.52, lng = 127.02),
            testPlace("out", lat = 38.50, lng = 128.00), // 셀 밖
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.researchHere(37.5, 127.0, 8.0) // 저줌 집계 경로
        advanceUntilIdle()
        val cell = vm.uiState.value.clusters.first { it.count == 2 }.cell!!

        val events = mutableListOf<MapEvent>()
        val job = launch { vm.events.toList(events) }
        vm.expandAggregatedCell(cell)
        advanceUntilIdle()
        job.cancel()

        val expand = events.filterIsInstance<MapEvent.ExpandCluster>().single()
        assertEquals(setOf("in1", "in2"), expand.places.map { it.id }.toSet())
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
