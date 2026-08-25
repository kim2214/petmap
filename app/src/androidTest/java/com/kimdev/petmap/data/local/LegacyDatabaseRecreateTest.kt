package com.kimdev.petmap.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
 * DatabaseModule.provideDatabase 와 동일한 순서·빌더 구성이어야 한다(계약 대상).
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

    private fun openLikeApp(backup: FavoriteBackup): PetMapDatabase {
        backup.preserveLegacyDatabase(dbFile, PetMapDatabase.VERSION)
        return Room.databaseBuilder(context, PetMapDatabase::class.java, TEST_DB)
            .createFromAsset("petmap.db")
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, *PetMapDatabase.RECREATED_FROM)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
            .also { db = it }
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
