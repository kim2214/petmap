package com.kimdev.petmap.core.update

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play 인앱 업데이트(유연 방식).
 *
 * - 새 버전이 나온 지 [MIN_STALENESS_DAYS] 일 이상 지났을 때만, 그리고 [PROMPT_INTERVAL_MS] 에 한 번만 묻는다
 *   (출시 직후 단계적 배포 중이거나 방금 거절한 사용자를 매번 붙잡지 않는다).
 * - 사용자가 동의하면 Play 가 백그라운드로 받는다. 다 받으면 앱이 백그라운드로 갈 때(onStop) [completeUpdate]
 *   를 불러 조용히 설치한다 — 쓰는 도중 재시작시키지 않고, 따로 "다시 시작" 안내를 띄우지 않아도 된다.
 *   이전 실행에서 받아 둔 업데이트도 시작 시 확인해 같은 방식으로 마무리한다.
 * - Play 로 설치하지 않은 빌드(디버그·사이드로드)에선 조회가 실패하거나 업데이트 없음으로 끝난다 → 정상.
 *
 * 원격 장소 데이터 갱신은 이 앱 버전 이상에서만 동작하므로 구버전 사용자를 옮기는 장치이기도 하다.
 */
class InAppUpdate(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    private val manager = AppUpdateManagerFactory.create(activity)
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var downloaded = false

    // Activity 가 STARTED 되기 전에 등록해야 한다 → onCreate 에서 생성
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) Log.d(TAG, "업데이트 거절/취소: ${result.resultCode}")
    }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) downloaded = true
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    /** 앱 실행(구성 변경 재생성 제외) 시 호출 */
    fun checkOnLaunch() {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    downloaded = true
                    return@addOnSuccessListener
                }
                val stale = (info.clientVersionStalenessDays() ?: 0) >= MIN_STALENESS_DAYS
                val now = System.currentTimeMillis()
                val due = now - prefs.getLong(KEY_LAST_PROMPT, 0L) >= PROMPT_INTERVAL_MS
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE ||
                    !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) || !stale || !due
                ) {
                    return@addOnSuccessListener
                }
                // 조회는 비동기라 그 사이 Activity 가 끝났을 수 있다
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                prefs.edit { putLong(KEY_LAST_PROMPT, now) }
                manager.startUpdateFlowForResult(
                    info,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
            .addOnFailureListener { Log.d(TAG, "업데이트 조회 실패(Play 설치 빌드가 아니면 정상): ${it.message}") }
    }

    override fun onStart(owner: LifecycleOwner) {
        manager.registerListener(installListener)
    }

    override fun onStop(owner: LifecycleOwner) {
        manager.unregisterListener(installListener)
        // 회전 등 구성 변경으로 잠깐 멈추는 경우는 제외 — 실제로 앱을 떠날 때만 설치
        if (downloaded && !activity.isChangingConfigurations) {
            downloaded = false
            manager.completeUpdate()
        }
    }

    private companion object {
        const val TAG = "InAppUpdate"
        const val PREFS = "in_app_update"
        const val KEY_LAST_PROMPT = "last_prompt_ms"
        const val MIN_STALENESS_DAYS = 2
        const val PROMPT_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000
    }
}
