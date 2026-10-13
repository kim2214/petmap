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
        // 화면은 소수 1자리로 보이므로 필터 값도 같게 맞춘다(5.55 → 표시 5.6 인데 필터 5.55 로 어긋나지 않게)
        val value = kg?.let(::roundToTenth)?.takeIf { it in PetProfileStore.MIN_KG..PetProfileStore.MAX_KG }
        prefs.edit {
            if (value == null) remove(KEY_WEIGHT) else putFloat(KEY_WEIGHT, value.toFloat())
        }
        _weightKg.value = value
    }

    private fun load(): Double? =
        // Float 저장값은 4.3f → 4.300000190… 처럼 늘어나 maxPetKg 4.3 경계에서 어긋나므로 다시 반올림
        if (prefs.contains(KEY_WEIGHT)) roundToTenth(prefs.getFloat(KEY_WEIGHT, 0f).toDouble()).takeIf { it > 0 } else null

    private fun roundToTenth(kg: Double): Double = Math.round(kg * 10) / 10.0

    private companion object {
        const val PREFS = "pet_profile"
        const val KEY_WEIGHT = "weight_kg"
    }
}
