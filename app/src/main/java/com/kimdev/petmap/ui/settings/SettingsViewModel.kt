package com.kimdev.petmap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimdev.petmap.data.local.FontScale
import com.kimdev.petmap.data.local.FontScaleStore
import com.kimdev.petmap.data.local.PetProfileStore
import com.kimdev.petmap.data.local.ThemeMode
import com.kimdev.petmap.data.local.ThemeStore
import com.kimdev.petmap.domain.model.DataInfo
import com.kimdev.petmap.domain.model.DataUpdateResult
import com.kimdev.petmap.domain.repository.PlaceDataUpdater
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 설정 화면 "새 데이터 확인" 행 상태 */
sealed interface DataUpdateUiState {
    data object Idle : DataUpdateUiState
    data object Checking : DataUpdateUiState
    data object UpToDate : DataUpdateUiState
    data object Updated : DataUpdateUiState
    data object Failed : DataUpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeStore: ThemeStore,
    private val fontScaleStore: FontScaleStore,
    private val petProfileStore: PetProfileStore,
    private val placeRepository: PlaceRepository,
    private val placeDataUpdater: PlaceDataUpdater,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeStore.mode

    fun setThemeMode(mode: ThemeMode) = themeStore.setMode(mode)

    val fontScale: StateFlow<FontScale> = fontScaleStore.scale

    fun setFontScale(scale: FontScale) = fontScaleStore.setScale(scale)

    /** 내 반려견 체중(kg). null = 미등록. '내 아이 가능' 필터의 기준값 */
    val petWeightKg: StateFlow<Double?> = petProfileStore.weightKg

    fun setPetWeight(kg: Double?) = petProfileStore.setWeight(kg)

    private val _dataInfo = MutableStateFlow<DataInfo?>(null)

    /** 데이터 기준일·장소 수. 조회 전/실패 시 null (행은 "불러오는 중" 표시) */
    val dataInfo: StateFlow<DataInfo?> = _dataInfo.asStateFlow()

    private val _dataUpdate = MutableStateFlow<DataUpdateUiState>(DataUpdateUiState.Idle)
    val dataUpdate: StateFlow<DataUpdateUiState> = _dataUpdate.asStateFlow()

    init {
        loadDataInfo()
    }

    private fun loadDataInfo() {
        viewModelScope.launch {
            _dataInfo.value = runCatching { placeRepository.getDataInfo() }.getOrNull()
        }
    }

    /** 원격 데이터를 바로 확인한다(주기 작업과 달리 모바일 데이터에서도). 확인 중 재탭은 무시 */
    fun checkDataUpdate() {
        if (_dataUpdate.value == DataUpdateUiState.Checking) return
        _dataUpdate.value = DataUpdateUiState.Checking
        viewModelScope.launch {
            _dataUpdate.value = when (placeDataUpdater.update()) {
                DataUpdateResult.UpToDate -> DataUpdateUiState.UpToDate
                is DataUpdateResult.Updated -> DataUpdateUiState.Updated.also { loadDataInfo() }
                is DataUpdateResult.Failed -> DataUpdateUiState.Failed
            }
        }
    }
}
