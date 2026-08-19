package com.kimdev.petmap.data.local

import android.content.Context
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 즐겨찾기 미러 파일 (Auto Backup 대상).
 *
 * 장소 DB(petmap.db)는 백업 한도 초과·createFromAsset 충돌 때문에 백업에서 제외되는데
 * (backup_rules.xml), favorites 테이블이 같은 DB 에 있어 즐겨찾기도 함께 사라졌다.
 * 즐겨찾기만 filesDir 의 JSON 으로 미러링해 백업/기기 이전에서 살아남게 하고,
 * 복원된 새 설치에서 DB 의 favorites 가 비어 있으면 미러를 다시 심는다
 * (PlaceRepositoryImpl.ensureSeeded → restoreFavoritesIfNeeded).
 */
@Singleton
class FavoriteBackup @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /** 현재 즐겨찾기 전체를 미러에 기록한다 (빈 목록이면 빈 배열 — 삭제도 반영). */
    fun write(favorites: List<FavoriteEntity>) {
        val array = JSONArray()
        favorites.forEach { f ->
            array.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("category", f.category)
                    put("roadAddress", f.roadAddress)
                    put("lat", f.lat)
                    put("lng", f.lng)
                    if (f.phone != null) put("phone", f.phone)
                    if (f.memo != null) put("memo", f.memo)
                    put("addedAt", f.addedAt)
                }
            )
        }
        // 임시 파일에 쓴 뒤 rename — 쓰는 도중 죽어도 기존 미러가 깨지지 않게 한다
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** 미러를 읽는다. 파일 없음/손상 시 빈 목록 (복원을 조용히 건너뛴다). */
    fun read(): List<FavoriteEntity> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    FavoriteEntity(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        category = o.getString("category"),
                        roadAddress = o.getString("roadAddress"),
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng"),
                        phone = if (o.has("phone")) o.getString("phone") else null,
                        // v4 이전 미러에는 없는 필드 — 기본값으로 폴백
                        memo = if (o.has("memo")) o.getString("memo") else null,
                        addedAt = o.optLong("addedAt", 0L),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "favorites_backup.json"
    }
}
