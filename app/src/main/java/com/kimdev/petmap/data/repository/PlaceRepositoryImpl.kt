package com.kimdev.petmap.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kimdev.petmap.data.local.PlaceFts
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.mapper.toDomain
import com.kimdev.petmap.data.mapper.toFavoriteEntity
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.cos
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val favoriteDao: FavoriteDao,
) : PlaceRepository {

    /**
     * DB 는 에셋(미리 시딩됨)에서 로드되므로 별도 시딩이 필요 없다.
     * 첫 접근 시 에셋 복사가 일어나도록 가벼운 쿼리로 워밍업만 한다.
     */
    override suspend fun ensureSeeded() {
        runCatching { placeDao.count() }
    }

    // 빈 카테고리 셋이면 IN () 오류 방지를 위해 더미 1개 + catCount=0 으로 전달
    private fun catsOf(categories: Set<PlaceCategory>): Pair<List<String>, Int> =
        if (categories.isEmpty()) listOf("") to 0
        else categories.map { it.name } to categories.size

    /** 경도 항 가중치 cos²(lat). 거리 정렬에서 경도 1도가 위도 1도보다 짧은 것을 보정한다. */
    private fun lngScaleSq(lat: Double): Double {
        val c = cos(Math.toRadians(lat))
        return c * c
    }

    override suspend fun getPlacesInBounds(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        limit: Int,
    ): List<Place> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * max(0.1, cos(Math.toRadians(centerLat))))
        val (cats, catCount) = catsOf(categories)
        return placeDao.getInBounds(
            minLat = centerLat - latDelta,
            maxLat = centerLat + latDelta,
            minLng = centerLng - lngDelta,
            maxLng = centerLng + lngDelta,
            centerLat = centerLat,
            centerLng = centerLng,
            lngScaleSq = lngScaleSq(centerLat),
            cats = cats,
            catCount = catCount,
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun getClusterCells(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        gridDivisions: Int,
        limit: Int,
    ): List<GeoClusterCell> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * max(0.1, cos(Math.toRadians(centerLat))))
        val minLat = centerLat - latDelta
        val maxLat = centerLat + latDelta
        val minLng = centerLng - lngDelta
        val maxLng = centerLng + lngDelta
        val divisions = max(1, gridDivisions)
        val latStep = (maxLat - minLat) / divisions
        val lngStep = (maxLng - minLng) / divisions
        val (cats, catCount) = catsOf(categories)
        return placeDao.getClusterCells(
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLng,
            maxLng = maxLng,
            latStep = latStep,
            lngStep = lngStep,
            cats = cats,
            catCount = catCount,
            limit = limit,
        ).map { GeoClusterCell(it.lat, it.lng, it.cnt) }
    }

    override suspend fun search(query: String, categories: Set<PlaceCategory>, limit: Int): List<Place> {
        val (cats, catCount) = catsOf(categories)
        val match = PlaceFts.match(query)
        val rows = if (match == null) placeDao.browse(cats, catCount, limit)
        else placeDao.searchByFts(ftsQuery(match, query, cats, catCount, userLat = null, userLng = null, limit = limit))
        return rows.map { it.toDomain() }
    }

    override suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        limit: Int,
    ): List<Place> {
        val (cats, catCount) = catsOf(categories)
        val match = PlaceFts.match(query)
        val rows = if (match == null) {
            placeDao.browseByDistance(cats, catCount, userLat, userLng, lngScaleSq(userLat), limit)
        } else {
            placeDao.searchByFts(ftsQuery(match, query, cats, catCount, userLat, userLng, limit))
        }
        // SQL 정렬은 근사(cos 보정)로 top-N 을 뽑고, 표시 순서는 정확한 거리로 다시 정렬한다.
        return rows.map { it.toDomain().copy(distanceMeters = distanceMeters(userLat, userLng, it.lat, it.lng)) }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
    }

    /**
     * FTS 검색 SQL 을 구성한다(카테고리 IN 절은 개수만큼 `?` 를 동적으로 만든다).
     * places_fts 는 places 의 rowid(docid) 를 참조하는 FTS4 테이블이다.
     * userLat/userLng 가 주어지면 해당 좌표에서 가까운 순, 아니면 관련도(이름 매칭 우선)순.
     */
    private fun ftsQuery(
        match: String,
        rawQuery: String,
        cats: List<String>,
        catCount: Int,
        userLat: Double?,
        userLng: Double?,
        limit: Int,
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any>(match)
        val sql = StringBuilder(
            "SELECT places.* FROM places " +
                "JOIN places_fts ON places.rowid = places_fts.docid " +
                "WHERE places_fts MATCH ?"
        )
        if (catCount != 0) {
            sql.append(" AND places.category IN (")
            sql.append(cats.joinToString(",") { "?" })
            sql.append(")")
            args.addAll(cats)
        }
        if (userLat != null && userLng != null) {
            sql.append(
                " ORDER BY ((places.lat - ?) * (places.lat - ?) + " +
                    "(places.lng - ?) * (places.lng - ?) * ?) ASC"
            )
            args.add(userLat); args.add(userLat); args.add(userLng); args.add(userLng)
            args.add(lngScaleSq(userLat))
        } else {
            // FTS 는 이름뿐 아니라 주소 매칭도 돌려주므로, 단순 이름 가나다순이면 상호를 정확히
            // 입력해도 무관한 결과 뒤로 밀린다 → 이름 시작 일치 > 이름 포함 > 그 외 순으로 정렬.
            val escaped = rawQuery.trim()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            sql.append(
                " ORDER BY CASE" +
                    " WHEN places.name LIKE ? ESCAPE '\\' THEN 0" +
                    " WHEN places.name LIKE ? ESCAPE '\\' THEN 1" +
                    " ELSE 2 END ASC, places.name ASC"
            )
            args.add("$escaped%")
            args.add("%$escaped%")
        }
        sql.append(" LIMIT ?")
        args.add(limit)
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    override suspend fun getPlace(id: String): Place? = placeDao.getById(id)?.toDomain()

    override fun observeFavorites(): Flow<List<Place>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { it.toSet() }

    override suspend fun toggleFavorite(place: Place) {
        if (favoriteDao.exists(place.id)) favoriteDao.deleteById(place.id)
        else favoriteDao.insert(place.toFavoriteEntity())
    }

}

/** 장소 목록에 즐겨찾기 상태를 결합 (ViewModel 에서 사용) */
fun List<Place>.withFavorites(favoriteIds: Set<String>): List<Place> =
    map { it.copy(isFavorite = it.id in favoriteIds) }
