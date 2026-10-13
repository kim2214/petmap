package com.kimdev.petmap.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kimdev.petmap.data.local.entity.PlaceEntity

/** [PlaceDao.getClusterCells] 결과: 그리드 셀의 대표 좌표(평균)와 장소 개수. */
data class ClusterCell(val lat: Double, val lng: Double, val cnt: Int)

/** [PlaceDao.getCoordsInRegion] 결과: 좌표만 (지역 범위 계산용 경량 로우) */
data class LatLngRow(val lat: Double, val lng: Double)

/** [PlaceDao.getRegionCounts] 결과: 주소 첫 토큰(시/도)·둘째 토큰(시/군/구 후보)별 장소 개수 */
data class RegionRow(val sido: String, val token: String, val cnt: Int)

@Dao
interface PlaceDao {

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    /** 단일 장소 */
    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaceEntity?

    /** 여러 장소 (순서 보장 없음 — 호출 측이 정렬). ids 는 SQLite 변수 한도(999) 아래로 넘긴다 */
    @Query("SELECT * FROM places WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<PlaceEntity>

    /**
     * 지도 뷰포트(위/경도 박스) 안의 장소. 중심에서 가까운 순으로 제한.
     * 카테고리 다중 필터: catCount 가 0 이면 전체, 아니면 category IN (cats).
     * (cats 는 빈 리스트일 때 SQL 오류가 나므로 항상 1개 이상 전달)
     * 동반 조건(anySize/indoor/outdoor/parking/myPetKg): 켜진 조건은 AND 로 걸리며 정보 없는 장소는 제외된다.
     * myPetKg(내 반려견 체중)는 null 이면 조건 없음, 아니면 maxPetKg >= 체중(maxPetKg NULL 은 제외)
     * ([com.kimdev.petmap.domain.model.PetFilter.matches] 와 같은 정책; sizeAll = PetFilter.SIZE_ALL).
     * 정렬: 경도 1도는 위도 1도보다 실제 거리가 짧으므로 lng 항에 cos²(lat)(=lngScaleSq)를 곱해 보정.
     */
    @Query(
        """
        SELECT * FROM places
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:catCount = 0 OR category IN (:cats))
          AND (:anySize = 0 OR allowedPetSize = :sizeAll)
          AND (:indoor = 0 OR indoorAllowed = 1)
          AND (:outdoor = 0 OR outdoorAllowed = 1)
          AND (:parking = 0 OR parkingAvailable = 1)
          AND (:myPetKg IS NULL OR maxPetKg >= :myPetKg)
        ORDER BY ((lat - :centerLat) * (lat - :centerLat)
                  + (lng - :centerLng) * (lng - :centerLng) * :lngScaleSq) ASC
        LIMIT :limit
        """
    )
    suspend fun getInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        centerLat: Double,
        centerLng: Double,
        lngScaleSq: Double,
        cats: List<String>,
        catCount: Int,
        anySize: Boolean,
        indoor: Boolean,
        outdoor: Boolean,
        parking: Boolean,
        sizeAll: String,
        myPetKg: Double?,
        limit: Int,
    ): List<PlaceEntity>

    /**
     * 저줌(광역) 개요용 그리드 집계. 뷰포트 박스를 latStep×lngStep 격자로 나눠
     * 셀별 대표 좌표(평균)와 개수만 반환한다.
     * 개별 로우/거리 정렬 없이 화면 전역을 커버하며, 반환 로우 수가 격자 수로 제한된다.
     * limit 이 격자 수보다 작아 잘릴 경우 큰 셀이 남도록 개수 내림차순으로 정렬한다.
     */
    @Query(
        """
        SELECT AVG(lat) AS lat, AVG(lng) AS lng, COUNT(*) AS cnt
        FROM places
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:catCount = 0 OR category IN (:cats))
          AND (:anySize = 0 OR allowedPetSize = :sizeAll)
          AND (:indoor = 0 OR indoorAllowed = 1)
          AND (:outdoor = 0 OR outdoorAllowed = 1)
          AND (:parking = 0 OR parkingAvailable = 1)
          AND (:myPetKg IS NULL OR maxPetKg >= :myPetKg)
        GROUP BY CAST((lat - :minLat) / :latStep AS INTEGER),
                 CAST((lng - :minLng) / :lngStep AS INTEGER)
        ORDER BY cnt DESC
        LIMIT :limit
        """
    )
    suspend fun getClusterCells(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        latStep: Double,
        lngStep: Double,
        cats: List<String>,
        catCount: Int,
        anySize: Boolean,
        indoor: Boolean,
        outdoor: Boolean,
        parking: Boolean,
        sizeAll: String,
        myPetKg: Double?,
        limit: Int,
    ): List<ClusterCell>

    /**
     * 검색어 없이 카테고리·동반 조건·지역만으로 이름순(sortKey: 한글→영문→숫자, 법인 표기 제외) 탐색 (동반 조건 정책은 [getInBounds] 와 동일).
     * region 은 [com.kimdev.petmap.domain.model.Region.prefix] ("경기도 고양시 ") 또는 "" (전체).
     */
    @Query(
        """
        SELECT * FROM places
        WHERE (:catCount = 0 OR category IN (:cats))
          AND (:anySize = 0 OR allowedPetSize = :sizeAll)
          AND (:indoor = 0 OR indoorAllowed = 1)
          AND (:outdoor = 0 OR outdoorAllowed = 1)
          AND (:parking = 0 OR parkingAvailable = 1)
          AND (:myPetKg IS NULL OR maxPetKg >= :myPetKg)
          AND (:region = '' OR roadAddress LIKE :region || '%')
        ORDER BY sortKey ASC
        LIMIT :limit
        """
    )
    suspend fun browse(
        cats: List<String>,
        catCount: Int,
        anySize: Boolean,
        indoor: Boolean,
        outdoor: Boolean,
        parking: Boolean,
        sizeAll: String,
        myPetKg: Double?,
        region: String,
        limit: Int,
    ): List<PlaceEntity>

    /**
     * 검색어 없이 카테고리·동반 조건 필터로, 지정 좌표에서 가까운 순 (lng 항에 cos²(lat) 보정).
     * min/max 박스로 반경을 제한한다(제한 없으면 전 세계 범위를 넘긴다).
     */
    @Query(
        """
        SELECT * FROM places
        WHERE (:catCount = 0 OR category IN (:cats))
          AND lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:anySize = 0 OR allowedPetSize = :sizeAll)
          AND (:indoor = 0 OR indoorAllowed = 1)
          AND (:outdoor = 0 OR outdoorAllowed = 1)
          AND (:parking = 0 OR parkingAvailable = 1)
          AND (:myPetKg IS NULL OR maxPetKg >= :myPetKg)
          AND (:region = '' OR roadAddress LIKE :region || '%')
        ORDER BY ((lat - :lat) * (lat - :lat)
                  + (lng - :lng) * (lng - :lng) * :lngScaleSq) ASC
        LIMIT :limit
        """
    )
    suspend fun browseByDistance(
        cats: List<String>,
        catCount: Int,
        lat: Double,
        lng: Double,
        lngScaleSq: Double,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        anySize: Boolean,
        indoor: Boolean,
        outdoor: Boolean,
        parking: Boolean,
        sizeAll: String,
        myPetKg: Double?,
        region: String,
        limit: Int,
    ): List<PlaceEntity>

    /**
     * 지역 브라우징용 집계: 도로명주소의 첫 토큰(시/도)과 둘째 토큰별 장소 개수.
     * 둘째 토큰이 시/군/구가 아닌 경우(세종의 읍·면·도로명)는 호출 측이 합친다.
     * 2.4만 행 문자열 함수 스캔이라 수십 ms — 저장소가 결과를 캐시한다.
     */
    @Query(
        """
        SELECT sido, token, COUNT(*) AS cnt FROM (
            SELECT substr(roadAddress, 1, instr(roadAddress, ' ') - 1) AS sido,
                   substr(substr(roadAddress, instr(roadAddress, ' ') + 1), 1,
                          instr(substr(roadAddress, instr(roadAddress, ' ') + 1), ' ') - 1) AS token
            FROM places
            WHERE instr(roadAddress, ' ') > 0
        )
        GROUP BY sido, token
        """
    )
    suspend fun getRegionCounts(): List<RegionRow>

    /** 지역(주소 접두어) 안 장소들의 좌표만. 지도에서 지역을 고르면 이 범위로 카메라를 맞춘다 */
    @Query("SELECT lat, lng FROM places WHERE roadAddress LIKE :region || '%'")
    suspend fun getCoordsInRegion(region: String): List<LatLngRow>

    /**
     * FTS 검색. places_fts 는 Migration(2,3) 이 만든 비-엔티티 FTS4 테이블이라
     * Room 이 컴파일타임에 스키마를 알 수 없으므로 RawQuery 로 실행한다.
     * SQL/바인딩은 [com.kimdev.petmap.data.repository.PlaceRepositoryImpl] 에서 구성한다.
     */
    @RawQuery
    suspend fun searchByFts(query: SupportSQLiteQuery): List<PlaceEntity>
}
