package com.kimdev.petmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.FavoriteWithPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // places 원본과 조인해 영업시간·반려동물 정보까지 채운다 (@Relation 은 @Transaction 필요)
    // 최근에 담은 것이 위로. 마이그레이션으로 addedAt 이 같은 기존 항목들은 이름순.
    @Transaction
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC, name ASC")
    fun observeAll(): Flow<List<FavoriteWithPlace>>

    @Query("SELECT id FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    // 삭제 직전 스냅샷(실행취소 복원용)
    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: String): FavoriteEntity?

    // 백업 미러 기록/복원용 (FavoriteBackup)
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    /** 지금 장소 데이터에 없는 즐겨찾기 (데이터에서 빠졌거나, 예전 데이터 갱신으로 id 가 바뀐 곳) */
    @Query("SELECT f.* FROM favorites f LEFT JOIN places p ON p.id = f.id WHERE p.id IS NULL")
    suspend fun getOrphans(): List<FavoriteEntity>

    /** 즐겨찾기 하나의 id 를 바꾼다 (메모·추가 시각 등은 [replacement] 가 들고 온다) */
    @Transaction
    suspend fun replace(oldId: String, replacement: FavoriteEntity) {
        deleteById(oldId)
        insert(replacement)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("UPDATE favorites SET memo = :memo WHERE id = :id")
    suspend fun updateMemo(id: String, memo: String?)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)
}
