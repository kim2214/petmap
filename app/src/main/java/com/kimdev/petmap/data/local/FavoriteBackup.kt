package com.kimdev.petmap.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
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
 *
 * 같은 경로가 DB 버전 업(파괴적 재생성) 때도 쓰인다: Room 이 구버전 DB 파일을 지우고 에셋을
 * 재복사하면 favorites 가 비므로, 미러가 즐겨찾기의 유일한 생존 경로다. 미러가 아직 없던 설치
 * (1.6 이하)를 위해 [preserveLegacyDatabase] 가 삭제 직전 파일에서 즐겨찾기를 건져 미러에 합친다.
 */
@Singleton
class FavoriteBackup internal constructor(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

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

    /**
     * Room 이 [dbFile] 을 지우고 에셋을 재복사하기 전에(= 파일 버전이 [currentVersion] 미만) 그 안의
     * favorites 를 읽어 미러에 합친다. 미러에 이미 있는 id 는 미러 값을 우선한다.
     *
     * DB 를 여는 코드(DatabaseModule.provideDatabase)보다 먼저 호출해야 한다. 파일 버전 판독은
     * Room 이 재복사 여부를 결정하는 방식(헤더 60~63 바이트)과 동일하게 해 판단이 어긋나지 않게 한다.
     * 어떤 실패도 앱 시작을 막지 않는다 — 최악의 경우 이전과 같은 즐겨찾기 소실일 뿐이다.
     */
    fun preserveLegacyDatabase(dbFile: File, currentVersion: Int) {
        runCatching {
            if (!dbFile.exists()) return
            val fileVersion = readUserVersion(dbFile)
            if (fileVersion <= 0 || fileVersion >= currentVersion) return
            val legacy = readLegacyFavorites(dbFile)
            if (legacy.isEmpty()) return
            val mirrored = read()
            val mirroredIds = mirrored.mapTo(HashSet()) { it.id }
            val added = legacy.filter { it.id !in mirroredIds }
            if (added.isEmpty()) return
            write(mirrored + added)
            Log.i(TAG, "구버전 DB(v$fileVersion) 즐겨찾기 ${added.size}건을 미러에 보존")
        }.onFailure { Log.w(TAG, "구버전 DB 즐겨찾기 보존 실패", it) }
    }

    /** SQLite 헤더의 user_version (오프셋 60, big-endian int). Room 의 readVersion 과 동일 */
    private fun readUserVersion(dbFile: File): Int =
        FileInputStream(dbFile).channel.use { channel ->
            val buffer = ByteBuffer.allocate(4)
            channel.position(60)
            if (channel.read(buffer) != 4) return -1
            buffer.rewind()
            buffer.int
        }

    /**
     * 구버전 favorites 테이블을 컬럼명 기준으로 읽는다 (v3: id~phone, v4: +memo·addedAt).
     * 필수 컬럼이 없으면 알 수 없는 스키마로 보고 빈 목록.
     */
    private fun readLegacyFavorites(dbFile: File): List<FavoriteEntity> {
        // WAL 파일이 남아 있을 수 있어 read-only 대신 read-write 로 연다 (곧 삭제될 파일이라 무해)
        val db = SQLiteDatabase.openDatabase(
            dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
        try {
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'favorites'", null,
            ).use { if (!it.moveToFirst()) return emptyList() }
            return db.rawQuery("SELECT * FROM favorites", null).use { c ->
                val id = c.getColumnIndex("id")
                val name = c.getColumnIndex("name")
                val category = c.getColumnIndex("category")
                val road = c.getColumnIndex("roadAddress")
                val lat = c.getColumnIndex("lat")
                val lng = c.getColumnIndex("lng")
                if (listOf(id, name, category, road, lat, lng).any { it < 0 }) return emptyList()
                val phone = c.getColumnIndex("phone")
                val memo = c.getColumnIndex("memo")
                val addedAt = c.getColumnIndex("addedAt")
                // 추가 시각을 모르는 구버전 항목은 보존 시점으로 채운다 (v3→v4 마이그레이션과 같은 정책)
                val now = System.currentTimeMillis()
                buildList {
                    while (c.moveToNext()) {
                        add(
                            FavoriteEntity(
                                id = c.getString(id),
                                name = c.getString(name),
                                category = c.getString(category),
                                roadAddress = c.getString(road) ?: "",
                                lat = c.getDouble(lat),
                                lng = c.getDouble(lng),
                                phone = if (phone >= 0) c.getString(phone) else null,
                                memo = if (memo >= 0) c.getString(memo) else null,
                                addedAt = if (addedAt >= 0) c.getLong(addedAt).takeIf { it > 0 } ?: now else now,
                            )
                        )
                    }
                }
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TAG = "FavoriteBackup"
        const val FILE_NAME = "favorites_backup.json"
    }
}
