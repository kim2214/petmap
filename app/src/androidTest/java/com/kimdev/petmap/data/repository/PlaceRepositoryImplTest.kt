package com.kimdev.petmap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimdev.petmap.data.local.FavoriteBackup
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.buildFtsIndex
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import com.kimdev.petmap.data.local.insertPlace
import com.kimdev.petmap.domain.model.PetFilter
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
import java.io.File

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
    private lateinit var mirrorFile: File
    private lateinit var backup: FavoriteBackup

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PetMapDatabase::class.java).build()
        // 실 filesDir 미러 대신 테스트별 임시 파일 — 토글이 미러 복원을 먼저 하므로 오염되면 결과가 흔들린다
        mirrorFile = File(context.cacheDir, "favorites_backup_repo_test.json").also { it.delete() }
        backup = FavoriteBackup(mirrorFile)
        repo = PlaceRepositoryImpl(db.placeDao(), db.favoriteDao(), db.metaDao(), backup, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
        mirrorFile.delete()
    }

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
    fun search_blankQueryBrowsesAll_symbolOnlyQueryReturnsNothing() = runBlocking {
        db.insertPlace("c1", name = "나비카페")
        db.insertPlace("c2", name = "가람식당", category = "RESTAURANT")
        db.buildFtsIndex()

        // 빈 검색어 → 전체 이름순 (목록 브라우징)
        assertEquals(listOf("가람식당", "나비카페"), repo.search("").map { it.name })
        // 색인 가능한 글자가 없는 검색어(match null) → 결과 없음. 전체 폴백은 "'!!' 검색 결과 200곳" 으로 오해된다
        assertTrue(repo.search("!!").isEmpty())
        assertTrue(repo.search("🐶").isEmpty())
    }

    @Test
    fun search_appliesPetFiltersInSql_beforeLimit() = runBlocking {
        // 이름순 앞쪽은 실외만, 뒤쪽 1건만 실내 가능. LIMIT 뒤에 걸렀다면 실내 1건이 잘려 나간다.
        (1..5).forEach { db.insertPlace("o$it", name = "가카페$it", indoorAllowed = false, outdoorAllowed = true) }
        db.insertPlace("i1", name = "하카페", indoorAllowed = true, outdoorAllowed = false, allowedPetSize = PetFilter.SIZE_ALL)
        db.buildFtsIndex()

        val indoor = repo.search("카페", petFilters = setOf(PetFilter.INDOOR), limit = 3)
        assertEquals(listOf("i1"), indoor.map { it.id })
        val indoorAnySize = repo.search("카페", petFilters = setOf(PetFilter.INDOOR, PetFilter.ANY_SIZE), limit = 3)
        assertEquals(listOf("i1"), indoorAnySize.map { it.id })
        // 검색어 없는 브라우징도 같은 조건을 쓴다
        assertEquals(listOf("i1"), repo.search("", petFilters = setOf(PetFilter.INDOOR), limit = 3).map { it.id })
        assertEquals(3, repo.search("", petFilters = setOf(PetFilter.OUTDOOR), limit = 3).size)
    }

    @Test
    fun search_open24h_matchesHoursOrNameWithoutHours() = runBlocking {
        db.insertPlace("h1", name = "해피동물병원", category = "HOSPITAL", operatingTime = "매일 00:00~24:00")
        db.insertPlace("h2", name = "24시 튼튼동물병원", category = "HOSPITAL") // 시간 정보 없음 → 상호로 판정
        db.insertPlace("h3", name = "24시 밤동물병원", category = "HOSPITAL", operatingTime = "매일 09:00~21:00") // 시간표 우선
        db.insertPlace("p1", name = "한강공원", category = "TRAVEL", operatingTime = "상시 개방")
        db.insertPlace("c1", name = "낮동물카페", operatingTime = "매일 10:00~22:00")
        db.buildFtsIndex()

        // 브라우징(@Query)과 FTS 검색(RawQuery: places_fts 에도 name 이 있어 places. 한정 필요) 모두
        assertEquals(setOf("h1", "h2", "p1"), repo.search("", petFilters = setOf(PetFilter.OPEN_24H)).map { it.id }.toSet())
        assertEquals(setOf("h1", "h2"), repo.search("동물병원", petFilters = setOf(PetFilter.OPEN_24H)).map { it.id }.toSet())
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
        // 삭제도 미러에 반영된다
        assertTrue(backup.read().isEmpty())
    }

    @Test
    fun removeFavorite_thenRestore_keepsMemoAndAddedAt() = runBlocking {
        db.insertPlace("p1", name = "단골카페")
        db.insertPlace("p2", name = "다른카페")
        val p1 = repo.getPlace("p1")!!
        val p2 = repo.getPlace("p2")!!
        repo.toggleFavorite(p1)
        repo.updateFavoriteMemo("p1", "주차는 뒷골목")
        repo.toggleFavorite(p2) // p1 보다 나중에 담김 → 최신순에서 위
        val addedAtBefore = db.favoriteDao().getById("p1")!!.addedAt

        val snapshot = repo.removeFavorite("p1")
        assertNotNull(snapshot)
        assertEquals("주차는 뒷골목", snapshot!!.place.memo)
        assertEquals(addedAtBefore, snapshot.addedAt)
        assertEquals(setOf("p2"), repo.observeFavoriteIds().first())
        assertEquals(listOf("p2"), backup.read().map { it.id })

        repo.restoreFavorite(snapshot)
        val restored = db.favoriteDao().getById("p1")!!
        assertEquals("주차는 뒷골목", restored.memo)
        assertEquals(addedAtBefore, restored.addedAt)
        // 원래 순서(p2 가 더 최신)로 돌아온다 — 토글 재추가였다면 p1 이 맨 위로 튀었을 것
        assertEquals(listOf("p2", "p1"), repo.observeFavorites().first().map { it.id })
        assertEquals(setOf("p1", "p2"), backup.read().map { it.id }.toSet())
    }

    @Test
    fun removeFavorite_returnsNullWhenNotFavorite() = runBlocking {
        db.insertPlace("p1")
        assertNull(repo.removeFavorite("p1"))
        assertNull(repo.removeFavorite("없는id"))
    }

    @Test
    fun toggleFavorite_restoresMirrorBeforeWriting() = runBlocking {
        // 딥링크 콜드스타트: ensureSeeded 없이 상세에서 바로 토글하는 경로.
        // 미러(백업 복원·버전 업 직후)에 2건이 있고 DB favorites 는 비어 있다.
        backup.write(
            listOf(
                FavoriteEntity("m1", "미러1", "CAFE", "서울 1", 37.5, 127.0, null, memo = "메모1", addedAt = 10L),
                FavoriteEntity("m2", "미러2", "CAFE", "서울 2", 37.5, 127.0, null, memo = null, addedAt = 20L),
            ),
        )
        db.insertPlace("p1")
        val place = repo.getPlace("p1")!!

        repo.toggleFavorite(place)

        // 기존 2건이 복원된 뒤 새 1건이 더해진다 — 이전에는 1건짜리 미러가 2건을 덮어 영구 소실됐다
        assertEquals(setOf("m1", "m2", "p1"), repo.observeFavoriteIds().first())
        assertEquals(setOf("m1", "m2", "p1"), backup.read().map { it.id }.toSet())
        assertEquals("메모1", db.favoriteDao().getById("m1")!!.memo)
    }

    @Test
    fun getPlace_mapsEntity_andFallsBackToEtcOnUnknownCategory() = runBlocking {
        db.insertPlace("p1", category = "CAFE")
        db.insertPlace("p2", category = "구버전카테고리")

        assertEquals(PlaceCategory.CAFE, repo.getPlace("p1")?.category)
        assertEquals(PlaceCategory.ETC, repo.getPlace("p2")?.category)
        assertNull(repo.getPlace("없는id"))
    }

    @Test
    fun getPlace_fallsBackToFavoriteSnapshot_whenRemovedFromData() = runBlocking {
        db.insertPlace("live", name = "영업중병원", category = "HOSPITAL")
        // 폐업 정리로 places 에서 빠졌지만 즐겨찾기에는 남은 장소
        db.favoriteDao().insert(FavoriteEntity("gone", "폐업병원", "HOSPITAL", "서울 어딘가", 37.5, 127.0, "02-1", memo = "메모"))

        val live = repo.getPlace("live")
        assertEquals(false, live?.isOutdated)

        val gone = repo.getPlace("gone")
        assertNotNull(gone)
        assertEquals("폐업병원", gone!!.name)
        assertEquals(PlaceCategory.HOSPITAL, gone.category)
        assertEquals("메모", gone.memo)
        assertTrue(gone.isFavorite && gone.isOutdated)

        assertNull(repo.getPlace("nowhere"))
    }

    @Test
    fun getPlaces_keepsRequestedOrder_andSkipsMissing() = runBlocking {
        db.insertPlace("p1")
        db.insertPlace("p2")
        db.insertPlace("p3")

        assertEquals(listOf("p3", "p1"), repo.getPlaces(listOf("p3", "없는id", "p1")).map { it.id })
        assertEquals(emptyList<String>(), repo.getPlaces(emptyList()).map { it.id })
    }
}
