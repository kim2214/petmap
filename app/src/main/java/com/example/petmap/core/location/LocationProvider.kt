package com.example.petmap.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class UserLocation(val lat: Double, val lng: Double)

/** FusedLocationProvider 래퍼. 권한 확인은 호출 측(UI)에서 처리한다. */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): UserLocation? {
        return runCatching {
            client.lastLocation.await()?.let { UserLocation(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
