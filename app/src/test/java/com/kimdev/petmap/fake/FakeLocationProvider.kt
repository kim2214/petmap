package com.kimdev.petmap.fake

import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.UserLocation

/** 고정 위치를 반환하는 LocationProvider 페이크. null 이면 위치 미허용 상황. */
class FakeLocationProvider(private val location: UserLocation? = null) : LocationProvider {
    override suspend fun lastLocation(): UserLocation? = location
    override suspend fun currentLocation(): UserLocation? = location
}
