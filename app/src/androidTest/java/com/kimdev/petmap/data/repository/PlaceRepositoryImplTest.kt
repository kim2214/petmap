package com.kimdev.petmap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.buildFtsIndex
import com.kimdev.petmap.data.local.insertPlace
import com.kimdev.petmap.domain.model.PlaceCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PlaceRepositoryImpl 통합 테스트 (실제 Room in-memory + 실제 DAO).
 * 반경→박스 변환, FTS/브라우즈 폴백 분기, 거리 채움·재정렬, 즐겨찾기 토글을
 * 알려진 좌표로 고정한다. (ViewModel 테스트는 FakePlaceRepository 를 쓰므로
 * 이 클래스가 실제 구현의 유일한 검증 지점이다.)
 */
@RunWith(AndroidJUnit4::class)
class PlaceRepositoryImplTest {

    private lateinit var db: PetMapDatabase
    private lateinit var repo: PlaceRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PetMapDatabase::class.java).build()
        repo = PlaceRepositoryImpl(db.placeDao(), db.favoriteDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getPlacesInBounds_convertsRadiusToLatLngBox() = runBlocking {
        // 반경 55.5km ≈ 위도 0.5도. 경도 델타는 cos(37.5) 보정으로 ≈ 0.63도.
        db.insertPlace("inLat", lat = 37.9, lng = 127.0)   // 0.4도 → 포함
        db.insertPlace("outLat", lat = 38.2, lng = 127.0)  // 0.7도 → 제외
        db.insertPlace("inLng", lat = 37.5, lng = 127.5)   // 0.5도 < 0.63 → 포함
        db.insertPlace("outLng", lat = 37.5, lng = 127.8)  // 0.8도 > 0.63 → 제외

        val result = repo.getPlacesInBounds(centerLat = 37.5, centerLng = 127.0, radiusKm = 55.5)
        assertEquals(setOf("inLat", "inLng"), result.map { it.id }.toSet())
    }

    @Test
    fun getClusterCells_dividesBoxIntoGrid() = runBlocking {
        db.insertPlace("a", lat = 37.45, lng = 126.95) // 중심 근처 셀
        db.insertPlace("b", lat = 37.46, lng = 126.96) // 같은 셀
        db.insertPlace("c", lat = 37.95, lng = 127.45) // 다른 셀

        val cells = repo.getClusterCells(
            centerLat = 37.5, centerLng = 127.0, radiusKm = 55.5,
            gridDivisions = 4, limit = 100,
        )
        assertEquals(2, cells.size)
        assertEquals(3, cells.sumOf { it.count })
        assertTrue(cells.any { it.count == 2 })
    }

    @Test
    fun search_blankOrSymbolQuery_fallsBackToBrowseAll() = runBlocking {
        db.insertPlace("c1", name = "나비카페")
        db.insertPlace("c2", name = "가람식당", category = "RESTAURANT")
        db.buildFtsIndex()

        // 빈 검색어 → 전체 이름순
        assertEquals(listOf("가람식당", "나비카페"), repo.search("").map { it.name })
        // 색인 가능한 글자가 없는 검색어(match null) → 동일 폴백
        assertEquals(listOf("가람식당", "나비카페"), repo.search("!!").map { it.name })
    }

    @Test
    fun search_usesFtsAndCategoryFilter() = runBlocking {
        db.insertPlace("c1", name = "행복카페", category = "CAFE")
        db.insertPlace("c2", name = "카페온더힐", category = "CAFE")
        db.insertPlace("r1", name = "카페같은식당", category = "RESTAURANT")
        db.buildFtsIndex()

        val all = repo.search("카페")
        assertEquals(setOf("c1", "c2", "r1"), all.map { it.id }.toSet())

        val cafesOnly = repo.search("카페", categories = setOf(PlaceCategory.CAFE))
        assertEquals(setOf("c1", "c2"), cafesOnly.map { it.id }.toSet())
    }

    @Test
    fun searchNearby_fillsDistanceAndSortsAscending() = runBlocking {
        db.insertPlace("far", name = "먼카페", lat = 38.0, lng = 127.0)   // ≈ 55.6km
        db.insertPlace("near", name = "가까운카페", lat = 37.51, lng = 127.0) // ≈ 1.1km
        db.buildFtsIndex()

        val result = repo.searchNearby("카페", emptySet(), userLat = 37.5, userLng = 127.0)
        assertEquals(listOf("near", "far"), result.map { it.id })
        val nearDist = result[0].distanceMeters
        assertNotNull(nearDist)
        assertTrue("near ≈ 1.1km 이어야 함: $nearDist", nearDist!! in 1000.0..1300.0)
        assertTrue(result[1].distanceMeters!! > nearDist)
    }

    @Test
    fun toggleFavorite_addsThenRemoves() = runBlocking {
        db.insertPlace("p1", name = "단골카페")
        val place = repo.getPlace("p1")!!

        repo.toggleFavorite(place)
        assertEquals(setOf("p1"), repo.observeFavoriteIds().first())
        assertEquals("단골카페", repo.observeFavorites().first().single().name)

        repo.toggleFavorite(place)
        assertEquals(emptySet<String>(), repo.observeFavoriteIds().first())
    }

    @Test
    fun getPlace_mapsEntity_andFallsBackToEtcOnUnknownCategory() = runBlocking {
        db.insertPlace("p1", category = "CAFE")
        db.insertPlace("p2", category = "구버전카테고리")

        assertEquals(PlaceCategory.CAFE, repo.getPlace("p1")?.category)
        assertEquals(PlaceCategory.ETC, repo.getPlace("p2")?.category)
        assertNull(repo.getPlace("없는id"))
    }
}
