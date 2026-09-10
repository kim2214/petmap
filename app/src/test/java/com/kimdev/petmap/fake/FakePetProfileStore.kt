package com.kimdev.petmap.fake

import com.kimdev.petmap.data.local.PetProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePetProfileStore(initial: Double? = null) : PetProfileStore {
    private val _weightKg = MutableStateFlow(initial)
    override val weightKg: StateFlow<Double?> = _weightKg
    override fun setWeight(kg: Double?) { _weightKg.value = kg }
}
