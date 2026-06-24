package com.example.petmap.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 하이브리드 갱신: 마지막 원격 동기화 시각을 기록한다. */
@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** 갱신 주기(기본 7일)가 지났는지 */
    fun isStale(now: Long, intervalMillis: Long = DEFAULT_INTERVAL): Boolean =
        now - lastSyncAt > intervalMillis

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_at"
        const val DEFAULT_INTERVAL = 7L * 24 * 60 * 60 * 1000 // 7일
    }
}
