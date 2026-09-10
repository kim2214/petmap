package com.kimdev.petmap.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 내 반려견 프로필(현재는 체중 kg 하나). "내 아이 가능" 필터([com.kimdev.petmap.domain.model.PetFilter.MY_PET])가
 * 장소별 입장 가능 최대 체중과 비교하는 기준값이다. 기기 로컬 SharedPreferences 에만 저장된다.
 */
interface PetProfileStore {
    /** 등록된 체중(kg). null = 미등록 */
    val weightKg: StateFlow<Double?>

    /** null 이면 등록 해제 */
    fun setWeight(kg: Double?)

    companion object {
        const val MIN_KG = 0.5
        const val MAX_KG = 100.0
    }
}

@Singleton
class PetProfileStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : PetProfileStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _weightKg = MutableStateFlow(load())
    override val weightKg: StateFlow<Double?> = _weightKg.asStateFlow()

    override fun setWeight(kg: Double?) {
        val value = kg?.takeIf { it in PetProfileStore.MIN_KG..PetProfileStore.MAX_KG }
        prefs.edit {
            if (value == null) remove(KEY_WEIGHT) else putFloat(KEY_WEIGHT, value.toFloat())
        }
        _weightKg.value = value
    }

    private fun load(): Double? =
        if (prefs.contains(KEY_WEIGHT)) prefs.getFloat(KEY_WEIGHT, 0f).toDouble().takeIf { it > 0 } else null

    private companion object {
        const val PREFS = "pet_profile"
        const val KEY_WEIGHT = "weight_kg"
    }
}
