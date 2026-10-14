package com.kimdev.petmap.data.update

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kimdev.petmap.core.common.IoDispatcher
import com.kimdev.petmap.data.local.PetMapDatabase
import com.kimdev.petmap.data.local.PlaceFts
import com.kimdev.petmap.data.local.entity.MetaEntity
import com.kimdev.petmap.data.remote.DataDownloader
import com.kimdev.petmap.data.remote.DataManifest
import com.kimdev.petmap.data.repository.PlaceRepositoryImpl
import com.kimdev.petmap.di.DataUpdateBaseUrl
import com.kimdev.petmap.domain.model.DataUpdateResult
import com.kimdev.petmap.domain.repository.PlaceDataUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원격 장소 데이터 갱신.
 *
 * 1. `<base>/<내 DB identity_hash>/manifest.json` 을 읽는다. 스키마(=identity_hash)별로 경로를 나누므로
 *    엔티티가 바뀐 새 앱용 데이터를 구버전 앱이 받는 일이 없다. 매니페스트가 없으면(404) 최신으로 본다.
 * 2. 기준일이 내 meta.data_date 보다 늦으면 gz 를 받아 SHA-256 을 확인하고 임시 DB 로 푼다.
 * 3. 임시 DB 를 검증한다: 무결성, identity_hash·기준일·장소 수가 매니페스트와 일치, FTS 색인 행 수,
 *    그리고 장소 수가 지금의 절반 미만으로 줄지 않았는지(잘못 만든 DB 게시 방지).
 * 4. Room 트랜잭션 하나로 places·places_fts·meta 를 통째로 교체한다 — 도중 실패하면 롤백되어
 *    기존 데이터가 남고, 읽기는 WAL 덕분에 커밋 전까지 이전 데이터를 본다.
 *
 * 파일 교체(DB 닫고 바꿔치기) 대신 행 복사를 쓰는 이유: Room 이 열어 둔 DB 를 안전하게 닫을 수 없고,
 * ATTACH 는 안드로이드 SQLiteDatabase 가 WAL 을 꺼 버린다. 즐겨찾기(favorites)는 별도 테이블이라 그대로다.
 */
@Singleton
class PlaceDataUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PetMapDatabase,
    private val downloader: DataDownloader,
    private val placeRepository: PlaceRepositoryImpl,
    @DataUpdateBaseUrl private val baseUrl: String,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaceDataUpdater {

    private val mutex = Mutex()

    override suspend fun update(): DataUpdateResult = mutex.withLock {
        withContext(ioDispatcher) {
            val workDir = File(context.cacheDir, WORK_DIR)
            try {
                runUpdate(workDir)
            } catch (e: Exception) {
                Log.w(TAG, "데이터 갱신 실패", e)
                DataUpdateResult.Failed(e)
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun runUpdate(workDir: File): DataUpdateResult {
        val live = database.openHelper.writableDatabase
        val identityHash = live.queryString("SELECT identity_hash FROM room_master_table WHERE id = 42")
            ?: throw IllegalStateException("room_master_table 에 identity_hash 없음")
        val localDate = live.queryString(
            "SELECT `value` FROM meta WHERE `key` = ?", MetaEntity.KEY_DATA_DATE,
        )?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val dirUrl = "${baseUrl.trimEnd('/')}/$identityHash/"
        val manifestJson = downloader.fetchText(dirUrl + MANIFEST, MAX_MANIFEST_BYTES)
            ?: return DataUpdateResult.UpToDate
        val manifest = DataManifest.parse(manifestJson)
        if (!manifest.isNewerThan(localDate)) return DataUpdateResult.UpToDate

        workDir.deleteRecursively()
        if (!workDir.mkdirs()) throw IOException("작업 디렉터리 생성 실패: $workDir")
        val gz = File(workDir, manifest.file)
        downloader.download(dirUrl + manifest.file, gz, manifest.size)
        if (gz.length() != manifest.size) throw IOException("크기 불일치: ${gz.length()} != ${manifest.size}")
        val actualSha = gz.sha256()
        if (actualSha != manifest.sha256) throw IOException("SHA-256 불일치")

        val candidate = File(workDir, CANDIDATE_DB)
        gunzip(gz, candidate)
        gz.delete()

        val currentCount = live.queryLong("SELECT COUNT(*) FROM places") ?: 0L
        SQLiteDatabase.openDatabase(
            candidate.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { src ->
            validate(src, manifest, identityHash, currentCount)
            replaceData(src)
        }
        placeRepository.onDataReplaced()
        Log.i(TAG, "데이터 갱신 완료: ${manifest.dataDate}, ${manifest.placeCount}곳")
        return DataUpdateResult.Updated(manifest.dataDate, manifest.placeCount)
    }

    private fun validate(src: SQLiteDatabase, manifest: DataManifest, identityHash: String, currentCount: Long) {
        val check = src.rawString("PRAGMA quick_check")
        if (check != "ok") throw IOException("무결성 검사 실패: $check")
        val hash = src.rawString("SELECT identity_hash FROM room_master_table WHERE id = 42")
        if (hash != identityHash) throw IOException("identity_hash 불일치: $hash")
        val date = src.rawString("SELECT `value` FROM meta WHERE `key` = '${MetaEntity.KEY_DATA_DATE}'")
        if (date != manifest.dataDate.toString()) throw IOException("기준일 불일치: $date")
        val count = src.rawString("SELECT COUNT(*) FROM places")?.toLongOrNull() ?: 0L
        if (count != manifest.placeCount.toLong()) throw IOException("장소 수 불일치: $count")
        if (count < currentCount * MIN_COUNT_RATIO) throw IOException("장소 수 급감: $currentCount → $count")
        val ftsCount = src.rawString("SELECT COUNT(*) FROM ${PlaceFts.TABLE}")?.toLongOrNull() ?: 0L
        if (ftsCount != count) throw IOException("FTS 색인 행 수 불일치: $ftsCount != $count")
    }

    /** places·FTS 색인·meta 를 한 트랜잭션으로 교체한다. 컬럼 목록은 내 DB 기준(identity_hash 가 같으니 동일) */
    @Suppress("DEPRECATION") // runInTransaction: SupportSQLite 모드에서 블로킹 트랜잭션의 표준 API
    private fun replaceData(src: SQLiteDatabase) {
        database.runInTransaction {
            val dst = database.openHelper.writableDatabase
            val placeColumns = dst.columnsOf("places")
            val metaColumns = dst.columnsOf("meta")
            dst.execSQL("DELETE FROM places")
            dst.execSQL("DELETE FROM ${PlaceFts.TABLE}")
            dst.execSQL("DELETE FROM meta")
            // rowid 를 그대로 옮겨야 places_fts.docid(== places.rowid) 조인이 맞는다
            copyRows(src, dst, "places", listOf("rowid") + placeColumns.map(::quote))
            copyRows(src, dst, PlaceFts.TABLE, listOf("docid", "name", "roadAddress"))
            copyRows(src, dst, "meta", metaColumns.map(::quote))
        }
    }

    private fun copyRows(src: SQLiteDatabase, dst: SupportSQLiteDatabase, table: String, columns: List<String>) {
        val cols = columns.joinToString(",")
        val stmt = dst.compileStatement(
            "INSERT INTO $table($cols) VALUES(${columns.joinToString(",") { "?" }})",
        )
        src.rawQuery("SELECT $cols FROM $table", null).use { c ->
            while (c.moveToNext()) {
                stmt.clearBindings()
                for (i in columns.indices) {
                    val index = i + 1
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> stmt.bindNull(index)
                        Cursor.FIELD_TYPE_INTEGER -> stmt.bindLong(index, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> stmt.bindDouble(index, c.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> stmt.bindBlob(index, c.getBlob(i))
                        else -> stmt.bindString(index, c.getString(i))
                    }
                }
                stmt.executeInsert()
            }
        }
    }

    private fun SupportSQLiteDatabase.columnsOf(table: String): List<String> =
        query("PRAGMA table_info($table)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(nameIndex)) }
        }

    private fun SupportSQLiteDatabase.queryString(sql: String, vararg args: Any?): String? =
        query(sql, args).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long? =
        query(sql).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    private fun SQLiteDatabase.rawString(sql: String): String? =
        rawQuery(sql, null).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }

    private fun quote(column: String) = "`$column`"

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun gunzip(gz: File, dest: File) {
        GZIPInputStream(gz.inputStream()).use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DB_BYTES) throw IOException("압축 해제 크기 상한 초과")
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private companion object {
        const val TAG = "PlaceDataUpdater"
        const val WORK_DIR = "data_update"
        const val MANIFEST = "manifest.json"
        const val CANDIDATE_DB = "candidate.db"
        const val MAX_MANIFEST_BYTES = 16 * 1024
        const val MAX_DB_BYTES = 200L * 1024 * 1024
        const val MIN_COUNT_RATIO = 0.5
        const val BUFFER = 64 * 1024
    }
}
