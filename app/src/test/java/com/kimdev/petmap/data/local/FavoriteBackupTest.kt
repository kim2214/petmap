package com.kimdev.petmap.data.local

import com.kimdev.petmap.data.local.entity.FavoriteEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

/**
 * 즐겨찾기 미러 파일의 기록/판독 계약.
 * (org.json 은 android.jar 스텁이라 testImplementation 에 실제 구현을 넣어야 돈다)
 */
class FavoriteBackupTest {

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var backup: FavoriteBackup

    @Before
    fun setUp() {
        dir = createTempDirectory("favbackup").toFile()
        file = File(dir, "favorites_backup.json")
        backup = FavoriteBackup(file)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entity(id: String, memo: String? = null, addedAt: Long = 0L) = FavoriteEntity(
        id = id, name = "이름$id", category = "CAFE", roadAddress = "서울 $id",
        lat = 37.5, lng = 127.0, phone = if (id.length % 2 == 0) "02-000-0000" else null,
        memo = memo, addedAt = addedAt,
    )

    @Test
    fun `write 후 read 는 모든 필드를 그대로 돌려준다`() {
        val favorites = listOf(entity("a", memo = "메모", addedAt = 1L), entity("bb", addedAt = 2L))

        backup.write(favorites)

        assertEquals(favorites, backup.read())
        assertFalse("임시 파일이 남으면 안 된다", File(dir, "favorites_backup.json.tmp").exists())
    }

    @Test
    fun `빈 목록도 기록된다 - 삭제 반영`() {
        backup.write(listOf(entity("a")))
        backup.write(emptyList())
        assertTrue(file.exists())
        assertTrue(backup.read().isEmpty())
    }

    @Test
    fun `파일 없음 또는 손상 시 빈 목록`() {
        assertTrue(backup.read().isEmpty())
        file.writeText("{ not json")
        assertTrue(backup.read().isEmpty())
    }

    @Test
    fun `동시에 여러 스레드가 써도 미러는 살아 있고 어느 한 스냅샷과 일치한다`() {
        // 수정 전: 임시 파일 이름이 고정이라 rename 이 서로 어긋나 file.delete() 뒤 미러가 사라지거나
        // 다른 스레드가 쓰는 중인 임시 파일을 옮겨 절반짜리 JSON 이 남을 수 있었다.
        val threads = 8
        val rounds = 25
        val snapshots = (0 until threads).map { t -> (0..t).map { entity("t$t-$it", addedAt = t.toLong()) } }
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(rounds) { backup.write(snapshots[t]) }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("쓰기 예외: $errors", errors.isEmpty())
        assertTrue("미러 파일이 사라졌다", file.exists())
        val read = backup.read()
        assertTrue("어느 스냅샷과도 일치하지 않음: ${read.map { it.id }}", read in snapshots)
        assertFalse(File(dir, "favorites_backup.json.tmp").exists())
    }
}
