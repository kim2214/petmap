package com.kimdev.petmap.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 내 글자 크기 배율. 시스템 글꼴 크기 위에 곱으로 적용된다(중장년 사용자 배려).
 * 작게(1.0 미만)는 접근성 역행이라 제공하지 않는다.
 */
enum class FontScale(val factor: Float) {
    NORMAL(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/**
 * 사용자가 선택한 글자 크기 배율(기기 로컬).
 * [ThemeStore] 와 같은 이유로 생성자에서 동기로 읽는다 — 첫 프레임 전에 값이 필요하고,
 * 비동기로 바꾸면 기본 크기로 한 프레임 그린 뒤 커져서 화면이 번쩍인다.
 */
@Singleton
class FontScaleStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("font_scale", Context.MODE_PRIVATE)
    private val _scale = MutableStateFlow(load())
    val scale: StateFlow<FontScale> = _scale.asStateFlow()

    fun setScale(scale: FontScale) {
        prefs.edit { putString(KEY, scale.name) }
        _scale.value = scale
    }

    private fun load(): FontScale =
        runCatching { FontScale.valueOf(prefs.getString(KEY, null) ?: FontScale.NORMAL.name) }
            .getOrDefault(FontScale.NORMAL)

    private companion object {
        const val KEY = "scale"
    }
}
