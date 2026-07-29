package com.kimdev.petmap.core.review

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.kimdev.petmap.BuildConfig

/**
 * Google Play 인앱 리뷰 요청.
 *
 * - 앱 실행 횟수가 [LAUNCH_THRESHOLD] 이상이 되는 시점에 1회만 요청한다.
 * - 인앱 리뷰는 Play 정책상 노출 빈도가 제한되며, Play를 통해 설치된 빌드에서만 실제 다이얼로그가 보인다.
 *   (디버그/사이드로드 빌드에서는 흐름만 완료되고 다이얼로그가 뜨지 않을 수 있음 → 정상)
 */
object InAppReview {
    private const val TAG = "InAppReview"
    private const val PREFS = "in_app_review"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_REQUESTED = "requested"
    private const val LAUNCH_THRESHOLD = 3

    /** 실행 시점에 호출. 조건 충족 시 리뷰 흐름을 띄운다. */
    fun maybeAsk(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REQUESTED, false)) return

        val launches = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit { putInt(KEY_LAUNCHES, launches) }
        if (launches < LAUNCH_THRESHOLD) return

        // 디버그 빌드에서는 실제 다이얼로그가 뜨지 않으므로 Fake 매니저로 흐름만 검증
        val manager = if (BuildConfig.DEBUG) {
            FakeReviewManager(activity)
        } else {
            ReviewManagerFactory.create(activity)
        }

        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.w(TAG, "requestReviewFlow failed", request.exception)
                return@addOnCompleteListener
            }
            // 요청 흐름은 비동기라 콜백 시점에 Activity 가 이미 파괴됐을 수 있다.
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                // 노출 여부와 무관하게 1회 요청 후 다시 묻지 않음
                prefs.edit { putBoolean(KEY_REQUESTED, true) }
            }
        }
    }
}
