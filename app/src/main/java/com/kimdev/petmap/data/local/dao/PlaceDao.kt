package com.kimdev.petmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
     */
    @Query(
        """
        SELECT * FROM places
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:catCount = 0 OR category IN (:cats))
        ORDER BY ((lat - :centerLat) * (lat - :centerLat) + (lng - :centerLng) * (lng - :centerLng)) ASC
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
        cats: List<String>,
        catCount: Int,
        limit: Int,
    ): List<PlaceEntity>

    /** 이름/주소 검색 + 카테고리 다중 필터 */
    @Query(
        """
        SELECT * FROM places
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR roadAddress LIKE '%' || :query || '%')
          AND (:catCount = 0 OR category IN (:cats))
        ORDER BY name ASC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, cats: List<String>, catCount: Int, limit: Int): List<PlaceEntity>

    /** 이름/주소 검색 + 카테고리 다중 필터, 지정 좌표에서 가까운 순 */
    @Query(
        """
        SELECT * FROM places
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR roadAddress LIKE '%' || :query || '%')
          AND (:catCount = 0 OR category IN (:cats))
        ORDER BY ((lat - :lat) * (lat - :lat) + (lng - :lng) * (lng - :lng)) ASC
        LIMIT :limit
        """
    )
    suspend fun searchByDistance(
        query: String,
        cats: List<String>,
        catCount: Int,
        lat: Double,
        lng: Double,
        limit: Int,
    ): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Query("DELETE FROM places")
    suspend fun clear()
}
