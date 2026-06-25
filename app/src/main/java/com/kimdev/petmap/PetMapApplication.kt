package com.kimdev.petmap

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PetMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 광고 SDK 초기화 (백그라운드 스레드에서 처리됨)
        MobileAds.initialize(this) {}
    }
}
