package com.kimdev.petmap.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 에셋 DB(assets/petmap.db) 로드 계약 테스트.
 *
 * 프리빌트 DB 는 tools/build_db.py 가 identity_hash/user_version 을 손으로 맞추는 구조라,
 * 엔티티 변경·에셋 재생성 시 동기화가 어긋나면 컴파일/유닛 테스트는 통과하고 런타임에만 터진다.
 * 이 테스트가 그 계약을 고정한다:
 *  1) createFromAsset → MIGRATION_2_3 → Room open (identity_hash 검증 포함) 이 성공한다.
 *  2) places 에 실데이터가 들어 있다.
 *  3) 마이그레이션이 만든 FTS 색인으로 검색이 실제 결과를 반환한다.
 */
@RunWith(AndroidJUnit4::class)
class PetMapDatabaseAssetTest {

    private lateinit var context: Context
    private lateinit var db: PetMapDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
        // DatabaseModule.provideDatabase 와 동일한 빌더 구성이어야 한다(계약 대상).
        db = Room.databaseBuilder(context, PetMapDatabase::class.java, TEST_DB)
            .createFromAsset("petmap.db")
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun assetDb_opensAndContainsPlaces() = runBlocking {
        // 첫 쿼리가 DB open 을 트리거한다. identity_hash 불일치(build_db.py 미동기화)나
        // 마이그레이션 실패는 여기서 예외로 드러난다.
        val count = db.placeDao().count()
        assertTrue("places=$count — 에셋 DB가 비어 있거나 로드가 잘못됨", count > 10_000)
    }

    @Test
    fun migration_buildsFtsIndex_andSearchReturnsResults() = runBlocking {
        val match = PlaceFts.match("카페")
        assertNotNull(match)
        // PlaceRepositoryImpl.ftsQuery 와 동일한 조인 형태
        val results = db.placeDao().searchByFts(
            SimpleSQLiteQuery(
                "SELECT places.* FROM places " +
                    "JOIN places_fts ON places.rowid = places_fts.docid " +
                    "WHERE places_fts MATCH ? LIMIT 5",
                arrayOf(match),
            )
        )
        assertTrue("FTS 검색 결과 0건 — places_fts 색인이 만들어지지 않았거나 비어 있음", results.isNotEmpty())
    }

    private companion object {
        // 실제 앱 DB(petmap.db)와 겹치지 않는 테스트 전용 파일명
        const val TEST_DB = "petmap-asset-test.db"
    }
}
