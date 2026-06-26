package com.kimdev.petmap.ui.settings

import androidx.lifecycle.ViewModel
import com.kimdev.petmap.data.local.ThemeMode
import com.kimdev.petmap.data.local.ThemeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeStore: ThemeStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeStore.mode

    fun setThemeMode(mode: ThemeMode) = themeStore.setMode(mode)
}
