package com.kimdev.petmap.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.PlaceFts
import com.kimdev.petmap.data.local.buildFtsIndex
import com.kimdev.petmap.data.local.insertPlace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PlaceDao SQL 정확성 테스트 (in-memory Room).
 * Room 은 컴파일 시 문법만 검증하므로, 박스 경계·거리 정렬·그리드 집계·FTS 조인의
 * "결과가 맞는지"는 여기서 알려진 좌표로 고정한다.
 */
@RunWith(AndroidJUnit4::class)
class PlaceDaoTest {

    private lateinit var db: PetMapDatabase
    private lateinit var dao: PlaceDao

    // 전체 카테고리 조회용 더미 (빈 IN () 방지 규약 — PlaceRepositoryImpl.catsOf 참고)
    private val allCats = listOf("") to 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PetMapDatabase::class.java).build()
        dao = db.placeDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getInBounds_filtersBoxAndOrdersByDistance() = runBlocking {
        db.insertPlace("far", lat = 37.9, lng = 127.0)     // 박스 안, 중심에서 멂
        db.insertPlace("center", lat = 37.5, lng = 127.0)  // 중심
        db.insertPlace("outLat", lat = 38.6, lng = 127.0)  // maxLat 밖
        db.insertPlace("outLng", lat = 37.5, lng = 128.2)  // maxLng 밖

        val result = dao.getInBounds(
            minLat = 36.5, maxLat = 38.5, minLng = 126.0, maxLng = 128.0,
            centerLat = 37.5, centerLng = 127.0, lngScaleSq = 0.63,
            cats = allCats.first, catCount = allCats.second, limit = 10,
        )
        assertEquals(listOf("center", "far"), result.map { it.id })
    }

    @Test
    fun getInBounds_lngDistanceUsesScaleCorrection() = runBlocking {
        // 위도 0.5도 차이 vs 경도 0.6도 차이: 보정(cos²≈0.63) 후 경도 쪽이 더 가깝다.
        // (0.5² = 0.25  >  0.6² × 0.63 = 0.227)
        db.insertPlace("latOff", lat = 38.0, lng = 127.0)
        db.insertPlace("lngOff", lat = 37.5, lng = 127.6)

        val result = dao.getInBounds(
            minLat = 36.0, maxLat = 39.0, minLng = 126.0, maxLng = 129.0,
            centerLat = 37.5, centerLng = 127.0, lngScaleSq = 0.63,
            cats = allCats.first, catCount = allCats.second, limit = 10,
        )
        assertEquals(listOf("lngOff", "latOff"), result.map { it.id })
    }

    @Test
    fun getInBounds_categoryFilterAndLimit() = runBlocking {
        db.insertPlace("cafeNear", category = "CAFE", lat = 37.5)
        db.insertPlace("cafeFar", category = "CAFE", lat = 37.8)
        db.insertPlace("hospital", category = "HOSPITAL", lat = 37.5)

        val cafes = dao.getInBounds(
            minLat = 36.5, maxLat = 38.5, minLng = 126.0, maxLng = 128.0,
            centerLat = 37.5, centerLng = 127.0, lngScaleSq = 0.63,
            cats = listOf("CAFE"), catCount = 1, limit = 10,
        )
        assertEquals(listOf("cafeNear", "cafeFar"), cafes.map { it.id })

        val limited = dao.getInBounds(
            minLat = 36.5, maxLat = 38.5, minLng = 126.0, maxLng = 128.0,
            centerLat = 37.5, centerLng = 127.0, lngScaleSq = 0.63,
            cats = listOf("CAFE"), catCount = 1, limit = 1,
        )
        assertEquals(listOf("cafeNear"), limited.map { it.id })
    }

    @Test
    fun getClusterCells_aggregatesPerGridCell() = runBlocking {
        // 2×2 격자 (latStep=lngStep=0.5): 좌하 셀에 2개, 우상 셀에 1개
        db.insertPlace("a", lat = 37.1, lng = 127.1)
        db.insertPlace("b", lat = 37.2, lng = 127.2)
        db.insertPlace("c", lat = 37.8, lng = 127.8)

        val cells = dao.getClusterCells(
            minLat = 37.0, maxLat = 38.0, minLng = 127.0, maxLng = 128.0,
            latStep = 0.5, lngStep = 0.5,
            cats = allCats.first, catCount = allCats.second, limit = 10,
        ).sortedBy { it.cnt }

        assertEquals(2, cells.size)
        assertEquals(1, cells[0].cnt)
        assertEquals(37.8, cells[0].lat, 1e-9)
        assertEquals(2, cells[1].cnt)
        assertEquals(37.15, cells[1].lat, 1e-9) // AVG(37.1, 37.2)
        assertEquals(127.15, cells[1].lng, 1e-9)
    }

    @Test
    fun browse_ordersByNameAndFiltersCategory() = runBlocking {
        db.insertPlace("c1", name = "다정카페", category = "CAFE")
        db.insertPlace("c2", name = "가온카페", category = "CAFE")
        db.insertPlace("h1", name = "나눔병원", category = "HOSPITAL")

        val all = dao.browse(allCats.first, allCats.second, limit = 10)
        assertEquals(listOf("가온카페", "나눔병원", "다정카페"), all.map { it.name })

        val cafes = dao.browse(listOf("CAFE"), 1, limit = 10)
        assertEquals(listOf("가온카페", "다정카페"), cafes.map { it.name })
    }

    @Test
    fun browseByDistance_ordersByProximity() = runBlocking {
        db.insertPlace("far", lat = 38.0, lng = 127.0)
        db.insertPlace("near", lat = 37.6, lng = 127.0)

        val result = dao.browseByDistance(
            cats = allCats.first, catCount = allCats.second,
            lat = 37.5, lng = 127.0, lngScaleSq = 0.63, limit = 10,
        )
        assertEquals(listOf("near", "far"), result.map { it.id })
    }

    @Test
    fun searchByFts_matchesNameAndAddressViaDocidJoin() = runBlocking {
        db.insertPlace("cafe", name = "행복카페", roadAddress = "서울 서초구 1")
        db.insertPlace("hospital", name = "튼튼동물병원", roadAddress = "서울 강남구 테헤란로 2")
        db.buildFtsIndex()

        fun query(match: String) = SimpleSQLiteQuery(
            "SELECT places.* FROM places " +
                "JOIN places_fts ON places.rowid = places_fts.docid " +
                "WHERE places_fts MATCH ? ORDER BY places.name ASC",
            arrayOf(PlaceFts.match(match)!!),
        )

        // 이름 부분검색 (공백 없이 붙은 상호에서 "카페")
        assertEquals(listOf("cafe"), dao.searchByFts(query("카페")).map { it.id })
        // 주소 검색
        assertEquals(listOf("hospital"), dao.searchByFts(query("강남")).map { it.id })
        // 불일치
        assertEquals(emptyList<String>(), dao.searchByFts(query("약국")).map { it.id })
    }

    @Test
    fun countAndGetById() = runBlocking {
        db.insertPlace("p1")
        db.insertPlace("p2")
        assertEquals(2, dao.count())
        assertEquals("p1", dao.getById("p1")?.id)
        assertEquals(null, dao.getById("없는id"))
    }
}
