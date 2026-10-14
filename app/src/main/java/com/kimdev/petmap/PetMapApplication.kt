package com.kimdev.petmap

import android.app.Application
import com.kimdev.petmap.data.update.PlaceDataUpdateWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PetMapApplication : Application() {
    // 광고 SDK 초기화는 사용자 동의(UMP) 확보 후 MainActivity 에서 수행한다.
    // (AdsConsent.gather → canRequestAds 시 MobileAds.initialize)

    override fun onCreate() {
        super.onCreate()
        // 원격 장소 데이터 확인(하루 1회, Wi-Fi). 등록만 하므로 시작 비용은 무시할 만하다
        PlaceDataUpdateWorker.schedule(this)
    }
}
