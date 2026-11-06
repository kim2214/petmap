package com.kimdev.petmap.data.local

import kotlinx.coroutines.flow.first
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimdev.petmap.data.repository.PlaceRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * DB 버전 업 = 파괴적 재생성 전략의 계약 테스트.
 *
 * 구버전(v3, 미러 없음) DB 파일이 있는 상태에서 앱이 올라오면:
 *  1) FavoriteBackup.preserveLegacyDatabase 가 파일 안의 즐겨찾기를 미러에 건져 두고,
 *  2) Room 이 파일을 지우고 에셋을 재복사해 최신 장소 데이터(meta 포함)를 받으며,
 *  3) ensureSeeded 가 빈 favorites 를 미러에서 복원한다.
 * 앱과 같은 [PetMapDatabase.build] 로 연다 — 보존은 생성 시점이 아니라 Room 이 DB 를 처음 여는 백그라운드
 * 실행기에서 돈다. 그 순서(보존 → Room 의 삭제·재복사)가 쿼리·Flow·직접 접근 어느 경로로 먼저 열려도 지켜지는지가
 * 이 테스트의 핵심이다.
 */
@RunWith(AndroidJUnit4::class)
class LegacyDatabaseRecreateTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var mirrorFile: File
    private var db: PetMapDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
        dbFile = context.getDatabasePath(TEST_DB)
        mirrorFile = File(context.cacheDir, "favorites_backup_test.json").also { it.delete() }
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(TEST_DB)
        mirrorFile.delete()
    }

    private fun createLegacyV3Database() {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            // v3 favorites 스키마 (memo/addedAt 없음) — app/schemas/**/3.json
            legacy.execSQL(
                "CREATE TABLE `favorites` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`roadAddress` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `phone` TEXT, PRIMARY KEY(`id`))",
            )
            legacy.execSQL(
                "INSERT INTO favorites VALUES ('legacy-1', '옛 즐겨찾기', 'CAFE', '서울 어딘가 1', 37.5, 127.0, NULL)",
            )
            legacy.version = 3
        }
    }

    private fun openLikeApp(backup: FavoriteBackup): PetMapDatabase =
        PetMapDatabase.build(context, TEST_DB, backup).also { db = it }

    @Test
    fun build_onMainThread_doesNoFileIo() {
        createLegacyV3Database()
        val backup = FavoriteBackup(mirrorFile)
        // 앱에선 ViewModel 주입(메인 스레드) 중에 만들어진다 — 여기서 파일을 건드리면 안 된다
        InstrumentationRegistry.getInstrumentation().runOnMainSync { openLikeApp(backup) }

        assertTrue("생성만으로 미러를 썼다", !mirrorFile.exists())
        assertEquals(3, SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version })
    }

    @Test
    fun firstOpenViaFlow_preservesLegacyFavoritesBeforeRecreate() = runBlocking {
        createLegacyV3Database()
        val backup = FavoriteBackup(mirrorFile)
        val opened = openLikeApp(backup)

        // ViewModel 이 가장 먼저 구독하는 즐겨찾기 Flow 가 첫 open 인 경우
        assertEquals(emptyList<String>(), opened.favoriteDao().observeIds().first())

        assertEquals(listOf("legacy-1"), backup.read().map { it.id })
        assertTrue("에셋이 재복사되지 않음", opened.placeDao().count() > 10_000)
    }

    @Test
    fun firstOpenViaDirectAccess_preservesLegacyFavorites() {
        createLegacyV3Database()
        val backup = FavoriteBackup(mirrorFile)
        val opened = openLikeApp(backup)

        // 원격 갱신 작업이 콜드 프로세스에서 먼저 도는 경우 (Room 실행기를 거치지 않는 직접 접근)
        opened.prepareForOpen()
        opened.openHelper.writableDatabase.query("SELECT COUNT(*) FROM places").use { it.moveToFirst() }

        assertEquals(listOf("legacy-1"), backup.read().map { it.id })
    }

    @Test
    fun journalMode_isWal() = runBlocking {
        val opened = openLikeApp(FavoriteBackup(mirrorFile))
        opened.placeDao().count()
        val mode = opened.openHelper.writableDatabase.query("PRAGMA journal_mode").use { it.moveToFirst(); it.getString(0) }
        assertEquals("wal", mode.lowercase())
    }

    @Test
    fun legacyFile_isReplacedByAsset_andFavoritesSurvive() = runBlocking {
        createLegacyV3Database()
        val backup = FavoriteBackup(mirrorFile)

        val opened = openLikeApp(backup)
        val repo = PlaceRepositoryImpl(opened.placeDao(), opened.favoriteDao(), opened.metaDao(), backup, Dispatchers.IO)
        repo.ensureSeeded()

        // 재복사: 구버전 파일에는 없던 places/meta 가 에셋에서 들어왔다
        assertTrue("에셋이 재복사되지 않음", opened.placeDao().count() > 10_000)
        assertNotNull(opened.metaDao().get("data_date"))
        // 보존·복원: 구버전 즐겨찾기가 살아 있다
        val favorites = opened.favoriteDao().getAll()
        assertEquals(listOf("legacy-1"), favorites.map { it.id })
        assertTrue("addedAt 은 보존 시점으로 채워져야 한다", favorites.single().addedAt > 0)
    }

    @Test
    fun preserveLegacy_keepsMirrorEntryWhenSameIdExists() = runBlocking {
        createLegacyV3Database()
        val backup = FavoriteBackup(mirrorFile)
        // 미러에 같은 id 가 메모와 함께 있으면 미러 값이 우선한다
        backup.write(
            listOf(
                com.kimdev.petmap.data.local.entity.FavoriteEntity(
                    id = "legacy-1", name = "옛 즐겨찾기", category = "CAFE", roadAddress = "서울 어딘가 1",
                    lat = 37.5, lng = 127.0, phone = null, memo = "미러 메모", addedAt = 1L,
                ),
            ),
        )

        backup.preserveLegacyDatabase(dbFile, PetMapDatabase.VERSION)

        val merged = backup.read()
        assertEquals(1, merged.size)
        assertEquals("미러 메모", merged.single().memo)
    }

    @Test
    fun preserveLegacy_isNoOpForCurrentVersionFile() {
        createLegacyV3Database()
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = PetMapDatabase.VERSION }
        val backup = FavoriteBackup(mirrorFile)

        backup.preserveLegacyDatabase(dbFile, PetMapDatabase.VERSION)

        assertTrue("현재 버전 파일은 건드리지 않아야 한다", backup.read().isEmpty())
    }

    private companion object {
        const val TEST_DB = "petmap-legacy-test.db"
    }
}
