package com.kimdev.petmap

import android.app.Application
import android.content.ComponentCallbacks2
import com.kimdev.petmap.data.update.PlaceDataUpdateWorker
import com.kimdev.petmap.ui.map.ClusterIconCache
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 클러스터 아이콘(최대 256장, 장당 약 100KB)은 프로세스 전역 캐시다. 백그라운드에서 시스템이 메모리를
        // 회수하려 할 때만 비운다 — UI_HIDDEN(홈으로 나갈 때마다)에 비우면 돌아올 때마다 다시 굽게 된다.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) ClusterIconCache.clear()
    }
}
