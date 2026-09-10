package com.kimdev.petmap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.local.FontScale
import com.kimdev.petmap.data.local.FontScaleStore
import com.kimdev.petmap.data.local.PetProfileStore
import com.kimdev.petmap.data.local.ThemeMode
import com.kimdev.petmap.data.local.ThemeStore
import com.kimdev.petmap.domain.model.DataInfo
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeStore: ThemeStore,
    private val fontScaleStore: FontScaleStore,
    private val petProfileStore: PetProfileStore,
    placeRepository: PlaceRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeStore.mode

    fun setThemeMode(mode: ThemeMode) = themeStore.setMode(mode)

    val fontScale: StateFlow<FontScale> = fontScaleStore.scale

    fun setFontScale(scale: FontScale) = fontScaleStore.setScale(scale)

    /** 내 반려견 체중(kg). null = 미등록. '내 아이 가능' 필터의 기준값 */
    val petWeightKg: StateFlow<Double?> = petProfileStore.weightKg

    fun setPetWeight(kg: Double?) = petProfileStore.setWeight(kg)

    /** 내장 데이터 기준일·장소 수. 조회 전/실패 시 null (행은 "불러오는 중" 표시) */
    val dataInfo: StateFlow<DataInfo?> = flow {
        emit(runCatching { placeRepository.getDataInfo() }.getOrNull())
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
}
