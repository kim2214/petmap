package com.kimdev.petmap.data.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kimdev.petmap.domain.model.DataUpdateResult
import com.kimdev.petmap.domain.repository.PlaceDataUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * 하루 한 번 원격 장소 데이터를 확인하는 주기 작업.
 *
 * 대부분은 몇백 바이트 매니페스트만 읽고 끝나지만, 새 데이터가 있으면 수 MB 를 받으므로
 * 비데이터 요금망(Wi-Fi)·배터리·저장 공간 여유가 있을 때만 돈다. 모바일 데이터에서 바로 받고 싶으면
 * 설정 화면의 "새 데이터 확인"을 쓴다.
 *
 * hilt-work(HiltWorkerFactory) 대신 EntryPoint 로 의존성을 꺼낸다 — 기본 WorkManager 초기화를 그대로 쓰고
 * Application·매니페스트를 바꾸지 않아도 된다.
 */
class PlaceDataUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdaterEntryPoint {
        fun placeDataUpdater(): PlaceDataUpdater
    }

    override suspend fun doWork(): Result {
        val updater = EntryPointAccessors.fromApplication(applicationContext, UpdaterEntryPoint::class.java)
            .placeDataUpdater()
        // 실패는 재시도하지 않는다 — 다음 주기(하루 뒤)에 다시 확인하면 충분하다
        return when (val result = updater.update()) {
            is DataUpdateResult.Failed -> Result.failure()
            else -> Result.success().also { Log.d(TAG, "데이터 확인: $result") }
        }
    }

    companion object {
        private const val TAG = "PlaceDataUpdateWorker"
        private const val UNIQUE_NAME = "place_data_update"

        /**
         * 앱 시작 시 호출. UPDATE 는 이미 등록된 작업의 주기 기준 시각을 유지한 채 요청(주기·제약)만
         * 바꾼다 — KEEP 이면 앱 업데이트로 조건을 바꿔도 기존 설치엔 첫 등록 때 요청이 영영 남는다.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlaceDataUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
