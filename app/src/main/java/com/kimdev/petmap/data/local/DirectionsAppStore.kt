package com.kimdev.petmap.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 길찾기를 열 지도 앱. 고른 앱이 없으면 다른 앱 → 기기 기본 지도 → 웹 순으로 넘어간다 */
enum class DirectionsApp { NAVER, KAKAO }

/** 사용자가 고른 길찾기 앱(기기 로컬). 기본은 네이버 지도(기존 동작) */
@Singleton
class DirectionsAppStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = prefs(context)
    private val _app = MutableStateFlow(read(prefs))
    val app: StateFlow<DirectionsApp> = _app.asStateFlow()

    fun setApp(app: DirectionsApp) {
        prefs.edit { putString(KEY, app.name) }
        _app.value = app
    }

    companion object {
        private const val PREFS = "directions_app"
        private const val KEY = "app"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun read(prefs: SharedPreferences): DirectionsApp =
            runCatching { DirectionsApp.valueOf(prefs.getString(KEY, null) ?: DirectionsApp.NAVER.name) }
                .getOrDefault(DirectionsApp.NAVER)

        /** 길찾기 버튼(Context 확장 함수)에서 Hilt 없이 읽는다 */
        fun readStored(context: Context): DirectionsApp = read(prefs(context))
    }
}
