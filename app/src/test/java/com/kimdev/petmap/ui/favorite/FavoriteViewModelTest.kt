package com.kimdev.petmap.ui.favorite

import androidx.lifecycle.SavedStateHandle
import com.kimdev.petmap.core.location.UserLocation
import com.kimdev.petmap.fake.FakeLocationProvider
import com.kimdev.petmap.fake.FakePlaceRepository
import com.kimdev.petmap.fake.FakeRecentPlaceStore
import com.kimdev.petmap.fake.testPlace
import com.kimdev.petmap.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    private fun viewModel(
        store: FakeRecentPlaceStore = FakeRecentPlaceStore(),
        location: UserLocation? = null,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = FavoriteViewModel(repo, store, FakeLocationProvider(location), saved)

    @Test
    fun `최근 본 장소는 최신 우선이고 사라진 장소는 빠진다`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeRecentPlaceStore(listOf("c", "gone", "a"))
        val vm = viewModel(store)
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()

        assertFalse(vm.recentState.value.isLoading)
        assertEquals(listOf("c", "a"), vm.recentState.value.places.map { it.id })

        store.add("a") // 다시 보면 맨 앞으로
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), vm.recentState.value.places.map { it.id })
    }

    @Test
    fun `원격 갱신으로 데이터가 바뀌면 최근 본 장소를 다시 조회한다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(FakeRecentPlaceStore(listOf("a")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals("a", vm.recentState.value.places.single().name)

        repo.dataset = listOf(testPlace("a", name = "새 이름"))
        repo.simulateDataReplaced()
        advanceUntilIdle()
        assertEquals("새 이름", vm.recentState.value.places.single().name)
    }

    @Test
    fun `최근 본 목록의 하트는 즐겨찾기와 동기화되고 토글해도 카드는 남는다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(FakeRecentPlaceStore(listOf("b")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()
        assertFalse(vm.recentState.value.places.single().isFavorite)

        vm.toggleFavorite(vm.recentState.value.places.single())
        advanceUntilIdle()
        assertTrue(vm.recentState.value.places.single().isFavorite)
    }

    @Test
    fun `clearRecent 는 목록을 비운다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(FakeRecentPlaceStore(listOf("a", "b")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()

        vm.clearRecent()
        advanceUntilIdle()
        assertTrue(vm.recentState.value.places.isEmpty())
    }

    @Test
    fun `최근 본 장소 하나를 지우고 실행취소하면 원래 자리로 돌아온다`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(FakeRecentPlaceStore(listOf("a", "b", "c")))
        vm.recentState.launchIn(backgroundScope)
        advanceUntilIdle()

        val index = vm.removeRecent("b")
        advanceUntilIdle()
        assertEquals(1, index)
        assertEquals(listOf("a", "c"), vm.recentState.value.places.map { it.id })
        assertEquals(null, vm.removeRecent("b")) // 이미 지운 장소

        vm.restoreRecent("b", index!!)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.recentState.value.places.map { it.id })
    }

    @Test
    fun `정렬 - 기본은 추가순, 이름순은 한글 영문 숫자 순, 저장된 정렬을 복원한다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("1", name = "24시 동물병원"), testPlace("2", name = "Cafe Dog"), testPlace("3", name = "나무 카페"), testPlace("4", name = "가람 약국"))
        listOf("1", "2", "3", "4").forEach { repo.toggleFavorite(repo.getPlace(it)!!) }
        val saved = SavedStateHandle()
        val vm = viewModel(saved = saved)
        vm.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
        val recentOrder = vm.uiState.value.favorites.map { it.id }

        vm.selectSort(FavoriteSort.NAME)
        advanceUntilIdle()
        assertEquals(listOf("가람 약국", "나무 카페", "Cafe Dog", "24시 동물병원"), vm.uiState.value.favorites.map { it.name })

        val restored = viewModel(saved = saved)
        restored.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(FavoriteSort.NAME, restored.uiState.value.sort)

        vm.selectSort(FavoriteSort.RECENT)
        advanceUntilIdle()
        assertEquals(recentOrder, vm.uiState.value.favorites.map { it.id })
    }

    @Test
    fun `가까운순은 위치로 거리를 채워 정렬하고, 위치가 없으면 바꾸지 않고 안내한다`() = runTest(mainDispatcherRule.testDispatcher) {
        repo.dataset = listOf(testPlace("far", lat = 37.0, lng = 127.0), testPlace("near", lat = 37.5, lng = 127.0))
        repo.toggleFavorite(repo.getPlace("near")!!)
        repo.toggleFavorite(repo.getPlace("far")!!)

        val vm = viewModel(location = UserLocation(37.5, 127.0))
        vm.uiState.launchIn(backgroundScope)
        vm.selectSort(FavoriteSort.DISTANCE)
        advanceUntilIdle()
        assertEquals(FavoriteSort.DISTANCE, vm.uiState.value.sort)
        assertEquals(listOf("near", "far"), vm.uiState.value.favorites.map { it.id })
        assertTrue(vm.uiState.value.favorites.all { it.distanceMeters != null })

        val noLoc = viewModel()
        noLoc.uiState.launchIn(backgroundScope)
        val events = mutableListOf<FavoriteEvent>()
        noLoc.events.onEach { events += it }.launchIn(backgroundScope)
        noLoc.selectSort(FavoriteSort.DISTANCE)
        advanceUntilIdle()
        assertEquals(FavoriteSort.RECENT, noLoc.uiState.value.sort)
        assertEquals(listOf<FavoriteEvent>(FavoriteEvent.LocationUnavailable), events)
    }
}
