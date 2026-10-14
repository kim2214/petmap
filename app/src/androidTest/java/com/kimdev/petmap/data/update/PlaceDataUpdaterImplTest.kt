package com.kimdev.petmap.data.update

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimdev.petmap.data.local.FavoriteBackup
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.PlaceFts
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.MetaEntity
import com.kimdev.petmap.data.remote.UrlDataDownloader
import com.kimdev.petmap.data.repository.PlaceRepositoryImpl
import com.kimdev.petmap.domain.model.DataUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

/**
 * 원격 데이터 갱신 계약 (실기기): 실제 에셋 DB 를 변형해 file:// "원격"으로 게시하고 교체를 검증한다.
 *  - 새 데이터면 places·FTS·meta 가 통째로 바뀌고 즐겨찾기는 남는다.
 *  - 매니페스트 없음/같은 기준일 → 받지 않음.
 *  - SHA-256·identity_hash 불일치, 장소 수 급감 → 실패하고 기존 데이터 유지.
 */
@RunWith(AndroidJUnit4::class)
class PlaceDataUpdaterImplTest {

    private lateinit var context: Context
    private lateinit var db: PetMapDatabase
    private lateinit var updater: PlaceDataUpdaterImpl
    private lateinit var remoteRoot: File
    private lateinit var mirrorFile: File
    private lateinit var identityHash: String
    private lateinit var assetDate: LocalDate
    private var assetCount = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, PetMapDatabase::class.java, TEST_DB)
            .createFromAsset("petmap.db")
            .build()
        remoteRoot = File(context.cacheDir, "updater_test_remote").apply { deleteRecursively(); mkdirs() }
        mirrorFile = File(context.cacheDir, "favorites_backup_updater_test.json").also { it.delete() }
        val repo = PlaceRepositoryImpl(
            db.placeDao(), db.favoriteDao(), db.metaDao(), FavoriteBackup(mirrorFile), Dispatchers.IO,
        )
        updater = PlaceDataUpdaterImpl(
            context, db, UrlDataDownloader(), repo, "file://${remoteRoot.path}/", Dispatchers.IO,
        )
        val live = db.openHelper.writableDatabase
        identityHash = live.query("SELECT identity_hash FROM room_master_table").use { it.moveToFirst(); it.getString(0) }
        assetCount = live.query("SELECT COUNT(*) FROM places").use { it.moveToFirst(); it.getLong(0) }
        assetDate = runBlocking { LocalDate.parse(db.metaDao().get(MetaEntity.KEY_DATA_DATE)) }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        remoteRoot.deleteRecursively()
        mirrorFile.delete()
    }

    @Test
    fun newerData_replacesPlacesFtsAndMeta_keepsFavorites() = runBlocking {
        db.favoriteDao().insert(FavoriteEntity("fav-1", "내 즐겨찾기", "CAFE", "서울", 37.5, 127.0, null, memo = "메모"))
        publish(NEW_DATE) { src ->
            // 첫 장소 이름 변경(+FTS 색인 동기화), 10곳 삭제
            src.execSQL("UPDATE places SET name = ? WHERE rowid = (SELECT MIN(rowid) FROM places)", arrayOf(RENAMED))
            src.execSQL(
                "UPDATE places_fts SET name = ? WHERE docid = (SELECT MIN(rowid) FROM places)",
                arrayOf(PlaceFts.index(RENAMED)),
            )
            src.execSQL("DELETE FROM places_fts WHERE docid IN (SELECT rowid FROM places ORDER BY rowid DESC LIMIT 10)")
            src.execSQL("DELETE FROM places WHERE rowid IN (SELECT rowid FROM places ORDER BY rowid DESC LIMIT 10)")
        }

        val result = updater.update()

        assertEquals(DataUpdateResult.Updated(NEW_DATE, (assetCount - 10).toInt()), result)
        assertEquals(assetCount - 10, db.placeDao().count().toLong())
        assertEquals(NEW_DATE.toString(), db.metaDao().get(MetaEntity.KEY_DATA_DATE))
        // FTS 조인(docid == rowid)이 새 데이터에서도 맞는다
        val hits = db.openHelper.writableDatabase.query(
            "SELECT places.name FROM places JOIN places_fts ON places.rowid = places_fts.docid " +
                "WHERE places_fts MATCH ?",
            arrayOf(PlaceFts.match(RENAMED)),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals(listOf(RENAMED), hits)
        // 즐겨찾기·메모는 그대로
        assertEquals("메모", db.favoriteDao().getById("fav-1")?.memo)
        // 작업 파일 정리
        assertTrue(File(context.cacheDir, "data_update").exists().not())

        // 같은 기준일이 됐으니 다시 받지 않는다
        assertEquals(DataUpdateResult.UpToDate, updater.update())
    }

    @Test
    fun noManifest_isUpToDate() = runBlocking {
        assertEquals(DataUpdateResult.UpToDate, updater.update())
        assertEquals(assetCount, db.placeDao().count().toLong())
    }

    @Test
    fun sameDate_isUpToDate_withoutDownloading() = runBlocking {
        publish(assetDate) {}
        // 파일을 지워도 결과가 같다 → 기준일 비교만으로 끝나고 내려받지 않는다
        File(remoteRoot, identityHash).listFiles { f -> f.name.endsWith(".gz") }!!.forEach { it.delete() }
        assertEquals(DataUpdateResult.UpToDate, updater.update())
    }

    @Test
    fun shaMismatch_failsAndKeepsData() = runBlocking {
        publish(NEW_DATE, sha256 = "0".repeat(64)) {}
        assertFailedAndUnchanged()
    }

    @Test
    fun identityHashMismatch_failsAndKeepsData() = runBlocking {
        publish(NEW_DATE) { src -> src.execSQL("UPDATE room_master_table SET identity_hash = 'other'") }
        assertFailedAndUnchanged()
    }

    @Test
    fun drasticShrink_failsAndKeepsData() = runBlocking {
        publish(NEW_DATE) { src ->
            src.execSQL("DELETE FROM places WHERE rowid > 100")
            src.execSQL("DELETE FROM places_fts WHERE docid > 100")
        }
        assertFailedAndUnchanged()
    }

    private suspend fun assertFailedAndUnchanged() {
        val result = updater.update()
        assertTrue("$result", result is DataUpdateResult.Failed)
        assertEquals(assetCount, db.placeDao().count().toLong())
        assertEquals(assetDate.toString(), db.metaDao().get(MetaEntity.KEY_DATA_DATE))
    }

    /**
     * 에셋 DB 사본을 [edit] 로 변형하고 기준일을 [date] 로 바꿔 tools/publish_db.py 와 같은 구조로 게시한다.
     * [sha256] 을 주면 매니페스트에 그 값을 적는다(무결성 실패 시뮬레이션).
     */
    private fun publish(date: LocalDate, sha256: String? = null, edit: (SQLiteDatabase) -> Unit) {
        val dir = File(remoteRoot, identityHash).apply { mkdirs() }
        val dbFile = File(remoteRoot, "candidate.db")
        context.assets.open("petmap.db").use { input -> dbFile.outputStream().use { input.copyTo(it) } }
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { src ->
            src.execSQL("UPDATE meta SET `value` = ? WHERE `key` = ?", arrayOf(date.toString(), MetaEntity.KEY_DATA_DATE))
            edit(src)
        }
        val placeCount = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
            src.rawQuery("SELECT COUNT(*) FROM places", null).use { it.moveToFirst(); it.getInt(0) }
        }
        val name = "petmap-$date.db.gz"
        val gz = File(dir, name)
        GZIPOutputStream(gz.outputStream()).use { out -> dbFile.inputStream().use { it.copyTo(out) } }
        dbFile.delete()
        val sha = sha256 ?: MessageDigest.getInstance("SHA-256").digest(gz.readBytes())
            .joinToString("") { "%02x".format(it) }
        File(dir, "manifest.json").writeText(
            """{"dataDate":"$date","file":"$name","sha256":"$sha","size":${gz.length()},"placeCount":$placeCount}""",
        )
    }

    private companion object {
        const val TEST_DB = "updater-test.db"
        const val RENAMED = "원격갱신테스트장소"
        val NEW_DATE: LocalDate = LocalDate.of(2099, 1, 1)
    }
}
