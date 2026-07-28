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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 광고 동의(UMP) 흐름.
 *
 * 1) 동의 정보 갱신(requestConsentInfoUpdate)
 * 2) 필요 시 동의 폼 표시(loadAndShowConsentFormIfRequired) — EEA 등에서만 노출
 * 3) 광고 요청이 가능해지면(canRequestAds) MobileAds 1회 초기화
 *
 * 국내(EEA 외)에서는 폼이 뜨지 않고 즉시 광고 가능 상태가 되어 바로 초기화된다.
 *
 * EEA 등에서 [privacyOptionsRequired] 가 true 면 사용자가 동의를 다시 변경할 수 있는 진입점을
 * 앱 안에 반드시 제공해야 한다(Google 정책). 설정 화면이 이 플래그를 보고 항목을 노출한다.
 */
object AdsConsent {
    private const val TAG = "AdsConsent"
    private val adsInitialized = AtomicBoolean(false)
    private val gathered = AtomicBoolean(false)

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    // 테스트 기기: 여기 등록된 기기는 실제 광고 단위 ID 라도 항상 "테스트 광고"만 받는다.
    // → 비공개 테스트(릴리스 빌드)에서 본인/테스터의 실광고 클릭으로 인한 무효 트래픽 방지.
    //   새 테스터 추가 시 logcat 의 "addTestDeviceHashedId(...)" 해시를 아래에 추가하면 됨.
    private val TEST_DEVICE_IDS = listOf(
        AdRequest.DEVICE_ID_EMULATOR,
        "84F68E9B362F87C6A0FFE379B1C0F2FC", // 개발 기기
    )

    /**
     * 프로세스당 1회만 수집한다. 구성 변경(회전·테마 전환)마다 재호출하면 동의 정보 갱신
     * 네트워크 요청이 반복되고, 폼이 필요한 지역에서는 이미 파괴된 Activity 로 폼을 띄우려 한다.
     */
    fun gather(activity: Activity) {
        if (!gathered.compareAndSet(false, true)) return
        val appContext = activity.applicationContext
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                // 동의 정보 갱신 성공 → 필요하면 폼 표시
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "consent form error: ${formError.errorCode} ${formError.message}")
                    }
                    updatePrivacyOptionsFlag(consentInfo)
                    maybeInitAds(appContext, consentInfo)
                }
            },
            { requestError ->
                // 갱신 실패해도 기존 동의 상태로 광고 가능하면 진행
                Log.w(TAG, "consent update failed: ${requestError.errorCode} ${requestError.message}")
                updatePrivacyOptionsFlag(consentInfo)
                maybeInitAds(appContext, consentInfo)
            },
        )

        // 이미 동의가 확보된 재실행 등에서는 즉시 초기화
        updatePrivacyOptionsFlag(consentInfo)
        maybeInitAds(appContext, consentInfo)
    }

    /**
     * 광고 개인정보 옵션 폼을 표시한다(설정 화면 진입점).
     * [privacyOptionsRequired] 가 true 인 지역에서만 의미가 있다.
     */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "privacy options error: ${formError.errorCode} ${formError.message}")
            }
            updatePrivacyOptionsFlag(UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun updatePrivacyOptionsFlag(consentInfo: ConsentInformation) {
        _privacyOptionsRequired.value = consentInfo.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun maybeInitAds(context: Context, consentInfo: ConsentInformation) {
        if (!consentInfo.canRequestAds()) return
        if (adsInitialized.getAndSet(true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(TEST_DEVICE_IDS).build(),
        )
        // Activity 가 아니라 애플리케이션 컨텍스트로 초기화(수명이 긴 SDK 가 Activity 를 잡지 않게)
        MobileAds.initialize(context) {}
    }
}
