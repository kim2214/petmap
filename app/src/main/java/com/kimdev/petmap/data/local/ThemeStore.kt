package com.kimdev.petmap.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 화면 테마 모드 (표시 라벨은 UI 계층에서 문자열 리소스로 해석) */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 사용자가 선택한 화면 테마(기기 로컬). 기본값은 시스템 설정 따름.
 *
 * 여기만 생성자에서 동기로 읽는다. 첫 프레임 전에 값이 필요하기 때문이다 —
 * 비동기로 바꾸면 기본 테마로 한 프레임 그린 뒤 전환되어 화면이 번쩍인다.
 * (프리퍼런스 하나뿐이라 비용이 작다. 다른 Store 는 비동기 로드를 쓴다)
 */
@Singleton
class ThemeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)

    private companion object {
        const val KEY = "mode"
    }
}
