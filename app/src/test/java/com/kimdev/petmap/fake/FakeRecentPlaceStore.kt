package com.kimdev.petmap.fake

import com.kimdev.petmap.data.local.RecentPlaceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 메모리 기반 RecentPlaceStore 페이크 (실제 구현과 동일하게 최신 우선·중복 제거·최대 MAX개). */
class FakeRecentPlaceStore(initial: List<String> = emptyList()) : RecentPlaceStore {
    private val _recent = MutableStateFlow(initial)
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun add(placeId: String) {
        if (placeId.isBlank()) return
        _recent.value = (listOf(placeId) + _recent.value.filter { it != placeId }).take(RecentPlaceStore.MAX)
    }

    override fun remove(placeId: String) {
        _recent.value = _recent.value.filter { it != placeId }
    }

    override fun clear() { _recent.value = emptyList() }
}
