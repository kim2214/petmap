package com.kimdev.petmap.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor
import androidx.room.Database
import androidx.room.RoomDatabase
import com.kimdev.petmap.data.local.dao.FavoriteDao
import com.kimdev.petmap.data.local.dao.MetaDao
import com.kimdev.petmap.data.local.dao.PlaceDao
import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.MetaEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity

// 스키마 JSON(app/schemas/)을 리포에 커밋한다. 엔티티 변경 시 identity_hash 가 어떻게 바뀌는지
// 추적하고, tools/build_db.py 의 ROOM_IDENTITY_HASH 와 대조하는 단일 기준이 된다.
@Database(
    entities = [FavoriteEntity::class, PlaceEntity::class, MetaEntity::class],
    version = PetMapDatabase.VERSION,
    exportSchema = true,
)
abstract class PetMapDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun placeDao(): PlaceDao
    abstract fun metaDao(): MetaDao

    /** [build] 가 채운다: DB 를 처음 열기 전에 한 번 돌아야 하는 준비(구버전 즐겨찾기 보존) */
    @Volatile
    private var beforeFirstOpen: (() -> Unit)? = null

    /**
     * Room 실행기를 거치지 않고 [openHelper] 로 직접 DB 를 여는 코드(원격 갱신)는 먼저 이것을 부른다.
     * 이미 돌았으면 즉시 반환한다.
     */
    fun prepareForOpen() {
        beforeFirstOpen?.invoke()
    }

    companion object {
        const val NAME = "petmap.db"

        /**
         * 에셋 DB(tools/build_db.py 의 DB_VERSION)와 항상 같아야 한다.
         *
         * 데이터 갱신 전략: 마이그레이션을 쓰지 않는다. 버전을 올리면 Room 이 기존 DB 파일을 지우고
         * 에셋을 다시 복사한다(파괴적 재생성 — [RECREATED_FROM]). 사용자 데이터인 즐겨찾기는
         * [FavoriteBackup] 미러가 재생성 전에 보존하고 PlaceRepositoryImpl.ensureSeeded 가 복원한다.
         * 따라서 장소 데이터만 갱신할 때도 이 버전을 올려야 기존 설치에 새 데이터가 전달된다.
         */
        const val VERSION = 10

        /**
         * 재생성으로 처리하는 구버전 목록. 버전을 올릴 때 직전 버전을 여기에 추가한다
         * (명시 목록으로 두는 이유: 실수로 빠뜨린 버전은 예외로 드러나야 개발 단계에서 잡힌다).
         */
        val RECREATED_FROM = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

        /**
         * 앱과 계측 테스트가 공유하는 DB 구성 (DatabaseModule 이 이것만 부른다).
         *
         * 구버전 DB 파일은 [RECREATED_FROM] 에 따라 첫 open 시 삭제·재복사된다. 그 전에 파일 안의 favorites 를
         * 미러에 합쳐 두어야 미러가 없던 1.6 이하 설치에서 바로 올라오는 사용자의 즐겨찾기가 남는다
         * ([FavoriteBackup.preserveLegacyDatabase]). 이 보존은 파일을 읽고(업그레이드 직후엔 SQLite 를 열고 JSON 을
         * 쓰고) 하므로 생성 시점(= ViewModel 주입, 메인 스레드)이 아니라 Room 이 DB 를 처음 여는 백그라운드
         * 실행기에서 한 번 돌린다. Room 의 쿼리·트랜잭션·Flow 는 모두 [setQueryExecutor] 로 준 실행기에서 DB 를
         * 열고, 그렇지 않은 직접 접근은 [prepareForOpen] 을 먼저 부른다. 순서 계약은 LegacyDatabaseRecreateTest.
         */
        fun build(context: Context, name: String, favoriteBackup: FavoriteBackup): PetMapDatabase {
            val dbFile = context.getDatabasePath(name)
            val preserve = OnceRunner { favoriteBackup.preserveLegacyDatabase(dbFile, VERSION) }
            val io = Dispatchers.IO.asExecutor()
            val executor = Executor { task -> io.execute { preserve.run(); task.run() } }
            return Room.databaseBuilder(context, PetMapDatabase::class.java, name)
                // 미리 시딩된 DB(에셋, FTS 색인·meta 동봉)를 사용 → 첫 실행 시 파싱/색인 없이 즉시 사용
                .createFromAsset("petmap.db")
                // 구버전 설치는 마이그레이션 대신 파일 삭제 + 에셋 재복사로 최신 장소 데이터를 받는다.
                // 목록에 없는 버전에서의 업그레이드 경로 누락은 예외로 드러나야 하므로 전체 fallback 은 쓰지 않는다.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, *RECREATED_FROM)
                // 다운그레이드(구 APK 사이드로드 등)도 파괴적 재생성
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                // 저사양(isLowRamDevice) 기기에서도 WAL 을 쓴다. 기본값(AUTOMATIC)은 그런 기기에서 TRUNCATE 로
                // 떨어져, 원격 갱신의 교체 트랜잭션(3만 행 삭제·삽입) 동안 지도·목록 읽기가 수 초 막힌다.
                // WAL 이면 읽기는 커밋 전까지 이전 데이터를 본다. 대가는 연결 몇 개분의 메모리.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryExecutor(executor)
                .build()
                .also { it.beforeFirstOpen = preserve::run }
        }
    }
}

/** 처음 한 번만 [block] 을 돌린다. 여러 스레드가 동시에 불러도 나머지는 끝날 때까지 기다린다 */
private class OnceRunner(private val block: () -> Unit) {
    @Volatile
    private var done = false

    fun run() {
        if (done) return
        synchronized(this) {
            if (done) return
            block()
            done = true
        }
    }
}
