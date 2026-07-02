package com.kimdev.petmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kimdev.petmap.data.local.entity.PlaceEntity

@Dao
interface PlaceDao {

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    /** 단일 장소 */
    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaceEntity?

    /**
     * 지도 뷰포트(위/경도 박스) 안의 장소. 중심에서 가까운 순으로 제한.
     * 카테고리 다중 필터: catCount 가 0 이면 전체, 아니면 category IN (cats).
     * (cats 는 빈 리스트일 때 SQL 오류가 나므로 항상 1개 이상 전달)
     * 정렬: 경도 1도는 위도 1도보다 실제 거리가 짧으므로 lng 항에 cos²(lat)(=lngScaleSq)를 곱해 보정.
     */
    @Query(
        """
        SELECT * FROM places
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:catCount = 0 OR category IN (:cats))
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
        limit: Int,
    ): List<PlaceEntity>

    /** 검색어 없이 카테고리 필터만 (이름순) */
    @Query(
        """
        SELECT * FROM places
        WHERE (:catCount = 0 OR category IN (:cats))
        ORDER BY name ASC
        LIMIT :limit
        """
    )
    suspend fun browse(cats: List<String>, catCount: Int, limit: Int): List<PlaceEntity>

    /** 검색어 없이 카테고리 필터만, 지정 좌표에서 가까운 순 (lng 항에 cos²(lat) 보정) */
    @Query(
        """
        SELECT * FROM places
        WHERE (:catCount = 0 OR category IN (:cats))
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
        limit: Int,
    ): List<PlaceEntity>

    /**
     * FTS 검색. places_fts 는 Migration(2,3) 이 만든 비-엔티티 FTS4 테이블이라
     * Room 이 컴파일타임에 스키마를 알 수 없으므로 RawQuery 로 실행한다.
     * SQL/바인딩은 [com.kimdev.petmap.data.repository.PlaceRepositoryImpl] 에서 구성한다.
     */
    @RawQuery
    suspend fun searchByFts(query: SupportSQLiteQuery): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Query("DELETE FROM places")
    suspend fun clear()
}
