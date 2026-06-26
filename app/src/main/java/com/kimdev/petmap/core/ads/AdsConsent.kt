package com.kimdev.petmap.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 광고 동의(UMP) 흐름.
 *
 * 1) 동의 정보 갱신(requestConsentInfoUpdate)
 * 2) 필요 시 동의 폼 표시(loadAndShowConsentFormIfRequired) — EEA 등에서만 노출
 * 3) 광고 요청이 가능해지면(canRequestAds) MobileAds 1회 초기화
 *
 * 국내(EEA 외)에서는 폼이 뜨지 않고 즉시 광고 가능 상태가 되어 바로 초기화된다.
 */
object AdsConsent {
    private const val TAG = "AdsConsent"
    private val adsInitialized = AtomicBoolean(false)

    // 테스트 기기: 여기 등록된 기기는 실제 광고 단위 ID 라도 항상 "테스트 광고"만 받는다.
    // → 비공개 테스트(릴리스 빌드)에서 본인/테스터의 실광고 클릭으로 인한 무효 트래픽 방지.
    //   새 테스터 추가 시 logcat 의 "addTestDeviceHashedId(...)" 해시를 아래에 추가하면 됨.
    private val TEST_DEVICE_IDS = listOf(
        AdRequest.DEVICE_ID_EMULATOR,
        "84F68E9B362F87C6A0FFE379B1C0F2FC", // 개발 기기
    )

    fun gather(activity: Activity) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // 동의 정보 갱신 성공 → 필요하면 폼 표시
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "consent form error: ${formError.errorCode} ${formError.message}")
                    }
                    maybeInitAds(activity, consentInfo)
                }
            },
            { requestError ->
                // 갱신 실패해도 기존 동의 상태로 광고 가능하면 진행
                Log.w(TAG, "consent update failed: ${requestError.errorCode} ${requestError.message}")
                maybeInitAds(activity, consentInfo)
            },
        )

        // 이미 동의가 확보된 재실행 등에서는 즉시 초기화
        maybeInitAds(activity, consentInfo)
    }

    private fun maybeInitAds(context: Context, consentInfo: ConsentInformation) {
        if (!consentInfo.canRequestAds()) return
        if (adsInitialized.getAndSet(true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(TEST_DEVICE_IDS).build(),
        )
        MobileAds.initialize(context) {}
    }
}
