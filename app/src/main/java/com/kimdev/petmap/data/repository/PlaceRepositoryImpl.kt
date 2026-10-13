package com.kimdev.petmap.data.repository

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kimdev.petmap.core.common.IoDispatcher
import com.kimdev.petmap.data.local.FavoriteBackup
import com.kimdev.petmap.data.local.PlaceFts
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.MetaDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.local.entity.MetaEntity
import com.kimdev.petmap.data.mapper.toDomain
import com.kimdev.petmap.data.mapper.toFavoriteEntity
import com.kimdev.petmap.domain.model.DataInfo
import com.kimdev.petmap.domain.model.FavoriteSnapshot
import com.kimdev.petmap.domain.model.GeoBounds
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.PetFilter
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.domain.model.Region
import com.kimdev.petmap.domain.model.RegionCount
import com.kimdev.petmap.domain.repository.PlaceRepository
import com.kimdev.petmap.domain.util.distanceMeters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

private const val TRIM_MIN_SAMPLES = 50
private const val TRIM_RATIO = 0.02

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val favoriteDao: FavoriteDao,
    private val metaDao: MetaDao,
    private val favoriteBackup: FavoriteBackup,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaceRepository {

    @Volatile
    private var favoritesRestored = false
    private val restoreMutex = Mutex()

    /** 즐겨찾기 DB 변경 + 미러 기록을 한 단위로 묶는 락 ([writeFavorites]) */
    private val favoriteMutex = Mutex()

    /**
     * DB 는 에셋(미리 시딩됨)에서 로드되므로 별도 시딩이 필요 없다.
     * 첫 접근 시 에셋 복사(구버전 파일이면 삭제 후 재복사)가 일어나도록 가벼운 쿼리로 워밍업만 하고,
     * favorites 가 비어 있으면(백업 복원 설치·버전 업 재생성) 즐겨찾기 미러를 DB 에 다시 심는다.
     *
     * 에셋 복사·open 실패(저장 공간 부족, 파일 손상 등)는 예외로 전파한다 — 삼키면 빈 지도가
     * "데이터 없음"으로 보여 사용자가 원인을 알 수 없다. 뷰모델이 재시도 가능한 오류 상태로 보여준다.
     * 즐겨찾기 복원 실패는 장소 데이터 사용을 막을 이유가 아니므로 로그만 남긴다.
     */
    override suspend fun ensureSeeded() {
        placeDao.count()
        runCatching { restoreFavoritesIfNeeded() }
            .onFailure { Log.w(TAG, "즐겨찾기 미러 복원 실패", it) }
    }

    override suspend fun getDataInfo(): DataInfo = withContext(ioDispatcher) {
        val date = metaDao.get(MetaEntity.KEY_DATA_DATE)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        DataInfo(dataDate = date, placeCount = placeDao.count())
    }

    /**
     * DB 의 favorites 가 비어 있고 미러 파일에 내용이 있으면 복원한다.
     * (petmap.db 는 Auto Backup 제외라 기기 이전 시 즐겨찾기는 미러로만 전달되고,
     *  DB 버전 업 시에는 파일이 통째로 재복사되어 favorites 가 빈다)
     */
    private suspend fun restoreFavoritesIfNeeded() {
        if (favoritesRestored) return
        restoreMutex.withLock {
            if (favoritesRestored) return
            withContext(ioDispatcher) {
                if (favoriteDao.count() == 0) {
                    val mirrored = favoriteBackup.read()
                    if (mirrored.isNotEmpty()) favoriteDao.insertAll(mirrored)
                }
            }
            favoritesRestored = true
        }
    }

    // 빈 카테고리 셋이면 IN () 오류 방지를 위해 더미 1개 + catCount=0 으로 전달
    private fun catsOf(categories: Set<PlaceCategory>): Pair<List<String>, Int> =
        if (categories.isEmpty()) listOf("") to 0
        else categories.map { it.name } to categories.size

    /** 반경(km) → [minLat, maxLat, minLng, maxLng] 위경도 박스 근사 (지도 뷰포트 조회와 같은 방식) */
    private fun boundingBox(lat: Double, lng: Double, radiusKm: Double): DoubleArray {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * max(0.1, cos(Math.toRadians(lat))))
        return doubleArrayOf(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta)
    }

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
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
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
            anySize = PetFilter.ANY_SIZE in petFilters,
            indoor = PetFilter.INDOOR in petFilters,
            outdoor = PetFilter.OUTDOOR in petFilters,
            parking = PetFilter.PARKING in petFilters,
            sizeAll = PetFilter.SIZE_ALL,
            myPetKg = PetFilter.effectiveWeight(petFilters, myPetKg),
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun getClusterCells(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
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
            anySize = PetFilter.ANY_SIZE in petFilters,
            indoor = PetFilter.INDOOR in petFilters,
            outdoor = PetFilter.OUTDOOR in petFilters,
            parking = PetFilter.PARKING in petFilters,
            sizeAll = PetFilter.SIZE_ALL,
            myPetKg = PetFilter.effectiveWeight(petFilters, myPetKg),
            limit = limit,
        ).map {
            // 대표 좌표(셀 내 평균)로 격자 인덱스를 역산해 셀 경계를 복원한다
            // (SQL 의 CAST 는 0 이상 값에서 floor 와 동일)
            val gLat = floor((it.lat - minLat) / latStep)
            val gLng = floor((it.lng - minLng) / lngStep)
            GeoClusterCell(
                lat = it.lat, lng = it.lng, count = it.cnt,
                minLat = minLat + gLat * latStep,
                maxLat = minLat + (gLat + 1) * latStep,
                minLng = minLng + gLng * lngStep,
                maxLng = minLng + (gLng + 1) * lngStep,
            )
        }
    }

    /** 집계 셀 범위 재조회 — 저줌 클러스터 탭 시 목록으로 펼치기 위해 셀 안의 실제 장소를 가져온다. */
    override suspend fun getPlacesInCell(
        cell: GeoClusterCell,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        limit: Int,
    ): List<Place> {
        val (cats, catCount) = catsOf(categories)
        return placeDao.getInBounds(
            minLat = cell.minLat,
            maxLat = cell.maxLat,
            minLng = cell.minLng,
            maxLng = cell.maxLng,
            centerLat = cell.lat,
            centerLng = cell.lng,
            lngScaleSq = lngScaleSq(cell.lat),
            cats = cats,
            catCount = catCount,
            anySize = PetFilter.ANY_SIZE in petFilters,
            indoor = PetFilter.INDOOR in petFilters,
            outdoor = PetFilter.OUTDOOR in petFilters,
            parking = PetFilter.PARKING in petFilters,
            sizeAll = PetFilter.SIZE_ALL,
            myPetKg = PetFilter.effectiveWeight(petFilters, myPetKg),
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun search(
        query: String,
        categories: Set<PlaceCategory>,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        region: Region?,
        limit: Int,
    ): List<Place> {
        val (cats, catCount) = catsOf(categories)
        val match = PlaceFts.match(query)
        val rows = when {
            match != null -> placeDao.searchByFts(
                ftsQuery(
                    match, query, cats, catCount, userLat = null, userLng = null, petFilters = petFilters,
                    myPetKg = PetFilter.effectiveWeight(petFilters, myPetKg), region = region, limit = limit,
                ),
            )
            // 검색어는 있는데 색인할 글자가 없다("🐶", "…") → 결과 없음. 전체 폴백은 오해를 낳는다.
            query.isNotBlank() -> return emptyList()
            else -> placeDao.browse(
                cats, catCount,
                anySize = PetFilter.ANY_SIZE in petFilters,
                indoor = PetFilter.INDOOR in petFilters,
                outdoor = PetFilter.OUTDOOR in petFilters,
                parking = PetFilter.PARKING in petFilters,
                sizeAll = PetFilter.SIZE_ALL,
                myPetKg = PetFilter.effectiveWeight(petFilters, myPetKg),
                region = region?.prefix ?: "",
                limit = limit,
            )
        }
        return rows.map { it.toDomain() }
    }

    override suspend fun searchNearby(
        query: String,
        categories: Set<PlaceCategory>,
        userLat: Double,
        userLng: Double,
        radiusKm: Double?,
        petFilters: Set<PetFilter>,
        myPetKg: Double?,
        region: Region?,
        limit: Int,
    ): List<Place> {
        val (cats, catCount) = catsOf(categories)
        val match = PlaceFts.match(query)
        val box = radiusKm?.let { boundingBox(userLat, userLng, it) } ?: WORLD_BOX
        val weight = PetFilter.effectiveWeight(petFilters, myPetKg)
        val rows = if (match == null) {
            placeDao.browseByDistance(
                cats, catCount, userLat, userLng, lngScaleSq(userLat),
                minLat = box[0], maxLat = box[1], minLng = box[2], maxLng = box[3],
                anySize = PetFilter.ANY_SIZE in petFilters,
                indoor = PetFilter.INDOOR in petFilters,
                outdoor = PetFilter.OUTDOOR in petFilters,
                parking = PetFilter.PARKING in petFilters,
                sizeAll = PetFilter.SIZE_ALL,
                myPetKg = weight,
                region = region?.prefix ?: "", limit = limit,
            )
        } else {
            placeDao.searchByFts(
                ftsQuery(
                    match, query, cats, catCount, userLat, userLng, box,
                    petFilters = petFilters, myPetKg = weight, region = region, limit = limit,
                ),
            )
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
        box: DoubleArray? = null,
        petFilters: Set<PetFilter> = emptySet(),
        myPetKg: Double? = null,
        region: Region? = null,
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
        if (box != null && box !== WORLD_BOX) {
            sql.append(" AND places.lat BETWEEN ? AND ? AND places.lng BETWEEN ? AND ?")
            args.add(box[0]); args.add(box[1]); args.add(box[2]); args.add(box[3])
        }
        // 동반 조건은 LIMIT 전에 SQL 로 걸어야 한다 — 결과를 잘라낸 뒤 클라이언트에서 거르면
        // 조건에 맞는 장소가 상한 밖으로 밀려 유실되고 개수·"이상" 표기도 틀린다.
        if (PetFilter.ANY_SIZE in petFilters) {
            sql.append(" AND places.allowedPetSize = ?")
            args.add(PetFilter.SIZE_ALL)
        }
        if (PetFilter.INDOOR in petFilters) sql.append(" AND places.indoorAllowed = 1")
        if (PetFilter.OUTDOOR in petFilters) sql.append(" AND places.outdoorAllowed = 1")
        if (PetFilter.PARKING in petFilters) sql.append(" AND places.parkingAvailable = 1")
        if (myPetKg != null) {
            sql.append(" AND places.maxPetKg >= ?")
            args.add(myPetKg)
        }
        if (region != null) {
            sql.append(" AND places.roadAddress LIKE ?")
            args.add(region.prefix + "%")
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
                    " ELSE 2 END ASC, places.sortKey ASC"
            )
            args.add("$escaped%")
            args.add("%$escaped%")
        }
        sql.append(" LIMIT ?")
        args.add(limit)
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }


    @Volatile
    private var regionCountsCache: List<RegionCount>? = null

    override suspend fun getRegionCounts(): List<RegionCount> {
        regionCountsCache?.let { return it }
        val rows = placeDao.getRegionCounts()
        // 시/군/구가 아닌 둘째 토큰(세종의 읍·면·도로명 등)은 시/도 아래 "기타" 한 행으로 합친다
        val merged = rows
            .groupBy { it.sido to (it.token.takeIf { t -> Region.isSigunguToken(t) }) }
            .map { (key, group) -> RegionCount(key.first, key.second, group.sumOf { it.cnt }) }
            .filter { it.sido.isNotBlank() }
        return merged.also { regionCountsCache = it }
    }

    override suspend fun getRegionBounds(region: Region): GeoBounds? {
        val coords = placeDao.getCoordsInRegion(region.prefix)
        if (coords.isEmpty()) return null
        // 경기도처럼 수천 곳인 시/도는 정렬 2회가 수 ms — 호출 측(Main)을 막지 않게 IO 에서
        return withContext(ioDispatcher) {
            val lats = coords.map { it.lat }.sorted()
            val lngs = coords.map { it.lng }.sorted()
            // 표본이 적으면 자르지 않는다(시/군/구 단위 수십 곳에서 2% 는 0~1개)
            val trim = if (coords.size >= TRIM_MIN_SAMPLES) (coords.size * TRIM_RATIO).toInt() else 0
            GeoBounds(
                minLat = lats[trim], maxLat = lats[lats.lastIndex - trim],
                minLng = lngs[trim], maxLng = lngs[lngs.lastIndex - trim],
            )
        }
    }

    override suspend fun getPlace(id: String): Place? = placeDao.getById(id)?.toDomain()

    override suspend fun getPlaces(ids: List<String>): List<Place> {
        if (ids.isEmpty()) return emptyList()
        val byId = placeDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    override fun observeFavorites(): Flow<List<Place>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { it.toSet() }

    override suspend fun toggleFavorite(place: Place) = writeFavorites {
        if (favoriteDao.exists(place.id)) favoriteDao.deleteById(place.id)
        else favoriteDao.insert(place.toFavoriteEntity())
    }

    override suspend fun updateFavoriteMemo(placeId: String, memo: String?) = writeFavorites {
        favoriteDao.updateMemo(placeId, memo?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun removeFavorite(placeId: String): FavoriteSnapshot? = writeFavorites {
        val entity = favoriteDao.getById(placeId) ?: return@writeFavorites null
        favoriteDao.deleteById(placeId)
        FavoriteSnapshot(place = entity.toDomain(), addedAt = entity.addedAt)
    }

    override suspend fun restoreFavorite(snapshot: FavoriteSnapshot) = writeFavorites {
        favoriteDao.insert(
            snapshot.place.toFavoriteEntity(memo = snapshot.place.memo, addedAt = snapshot.addedAt)
        )
    }

    /**
     * 즐겨찾기 변경의 공통 골격. 세 가지를 보장한다.
     * 1. **복원 우선**: 미러 복원이 아직 안 됐으면(딥링크 콜드스타트처럼 ensureSeeded 를 거치지 않은
     *    경로) 먼저 복원한다. 그러지 않으면 1건짜리 즐겨찾기가 미러를 덮어써 나머지가 영구 소실된다
     *    (count()!=0 이 되어 이후 복원이 스킵됨).
     * 2. **직렬화**: DB 변경과 미러 기록을 [favoriteMutex] 안에서 한 단위로 처리해, 서로 다른
     *    ViewModel 의 연속 토글이 오래된 스냅샷을 나중에 기록하거나 임시 파일을 서로 덮지 않게 한다.
     * 3. **취소 불가**: 토글 직후 화면을 떠나 viewModelScope 가 취소돼도 DB 변경과 미러가 어긋나지
     *    않도록 [NonCancellable] 로 감싼다. 작업 자체는 수 ms 짜리 로컬 I/O 라 부담이 없다.
     *
     * 미러 기록 실패는 원 작업을 되돌릴 이유가 아니라 로그만 남긴다 (다음 변경에서 재기록).
     */
    private suspend fun <T> writeFavorites(block: suspend () -> T): T =
        withContext(NonCancellable) {
            favoriteMutex.withLock {
                runCatching { restoreFavoritesIfNeeded() }
                    .onFailure { Log.w(TAG, "즐겨찾기 미러 복원 실패", it) }
                val result = block()
                runCatching {
                    withContext(ioDispatcher) { favoriteBackup.write(favoriteDao.getAll()) }
                }.onFailure { Log.w(TAG, "즐겨찾기 미러 기록 실패", it) }
                result
            }
        }

    private companion object {
        const val TAG = "PlaceRepository"
        /** 반경 제한 없음 — 전 세계 박스 (DAO 파라미터를 항상 채우기 위한 값) */
        val WORLD_BOX = doubleArrayOf(-90.0, 90.0, -180.0, 180.0)
    }
}

/** 장소 목록에 즐겨찾기 상태를 결합 (ViewModel 에서 사용) */
fun List<Place>.withFavorites(favoriteIds: Set<String>): List<Place> =
    map { it.copy(isFavorite = it.id in favoriteIds) }

