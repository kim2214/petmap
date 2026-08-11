package com.kimdev.petmap.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class UserLocation(val lat: Double, val lng: Double)

/** 마지막 위치 조회 추상화. 권한 확인은 호출 측(UI)에서 처리한다. */
interface LocationProvider {
    /** 마지막으로 알려진 위치. 권한 미허용·위치 없음·실패 시 null. */
    suspend fun lastLocation(): UserLocation?

    /**
     * 단발성 현재 위치 요청. 콜드스타트 직후처럼 [lastLocation] 이 null 일 때 보완용.
     * 권한 미허용·위치 없음·실패 시 null.
     */
    suspend fun currentLocation(): UserLocation?
}

/** FusedLocationProvider 래퍼. */
@Singleton
class LocationProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): UserLocation? =
        runCatching {
            client.lastLocation.await()?.let { UserLocation(it.latitude, it.longitude) }
        }.getOrElse {
            Log.w(TAG, "lastLocation failed: ${it.message}")
            null
        }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): UserLocation? =
        runCatching {
            // 실내·기내 모드 등 fix 를 못 잡는 상황에서 무기한 대기하지 않도록 타임아웃을 두고,
            // 초과 시 토큰을 취소해 GPS 요청 자체를 정리한다.
            val cts = CancellationTokenSource()
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .await()?.let { UserLocation(it.latitude, it.longitude) }
            }.also { if (it == null) cts.cancel() }
        }.getOrElse {
            Log.w(TAG, "currentLocation failed: ${it.message}")
            null
        }

    companion object {
        private const val TAG = "LocationProvider"
        private const val CURRENT_LOCATION_TIMEOUT_MS = 8_000L
    }
}
