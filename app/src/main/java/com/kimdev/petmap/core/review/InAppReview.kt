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
 * - 콜드스타트 직후가 아니라, **긍정 시그널(길찾기·공유)이 [SIGNAL_THRESHOLD] 회 쌓인 뒤
 *   다음 앱 실행 시점**에 1회만 요청한다. 앱을 잘 쓰고 있다는 근거가 생긴 뒤에 묻고,
 *   시그널 직후는 다른 앱(지도·공유 대상)으로 전환되는 순간이라 노출 시점으로 부적합하다.
 * - 인앱 리뷰는 Play 정책상 노출 빈도가 제한되며, Play를 통해 설치된 빌드에서만 실제 다이얼로그가 보인다.
 *   (디버그/사이드로드 빌드에서는 흐름만 완료되고 다이얼로그가 뜨지 않을 수 있음 → 정상)
 */
object InAppReview {
    private const val TAG = "InAppReview"
    private const val PREFS = "in_app_review"
    private const val KEY_SIGNALS = "positive_signals"
    private const val KEY_REQUESTED = "requested"
    private const val SIGNAL_THRESHOLD = 2

    /** 긍정 시그널(길찾기 실행·장소 공유) 시점에 호출 — 카운트만 쌓는다. */
    fun recordPositiveSignal(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REQUESTED, false)) return
        prefs.edit { putInt(KEY_SIGNALS, prefs.getInt(KEY_SIGNALS, 0) + 1) }
    }

    /** 앱 실행 시점에 호출. 긍정 시그널이 충분히 쌓였으면 리뷰 흐름을 띄운다. */
    fun maybeAsk(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REQUESTED, false)) return
        if (prefs.getInt(KEY_SIGNALS, 0) < SIGNAL_THRESHOLD) return

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
