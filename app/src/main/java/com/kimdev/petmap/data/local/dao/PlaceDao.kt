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
     * category 가 null 이면 전체 카테고리.
     */
    @Query(
        """
        SELECT * FROM places
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND (:category IS NULL OR category = :category)
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
        category: String?,
        limit: Int,
    ): List<PlaceEntity>

    /** 이름/주소 검색 + 카테고리 필터 */
    @Query(
        """
        SELECT * FROM places
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR roadAddress LIKE '%' || :query || '%')
          AND (:category IS NULL OR category = :category)
        ORDER BY name ASC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, category: String?, limit: Int): List<PlaceEntity>

    /** 이름/주소 검색 + 카테고리 필터, 지정 좌표에서 가까운 순 */
    @Query(
        """
        SELECT * FROM places
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR roadAddress LIKE '%' || :query || '%')
          AND (:category IS NULL OR category = :category)
        ORDER BY ((lat - :lat) * (lat - :lat) + (lng - :lng) * (lng - :lng)) ASC
        LIMIT :limit
        """
    )
    suspend fun searchByDistance(
        query: String,
        category: String?,
        lat: Double,
        lng: Double,
        limit: Int,
    ): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Query("DELETE FROM places")
    suspend fun clear()
}
