package com.kimdev.petmap

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PetMapApplication : Application() {
    // 광고 SDK 초기화는 사용자 동의(UMP) 확보 후 MainActivity 에서 수행한다.
    // (AdsConsent.gather → canRequestAds 시 MobileAds.initialize)
}
