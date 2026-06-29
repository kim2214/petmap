package com.kimdev.petmap.fake

import com.kimdev.petmap.data.local.RecentSearchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 메모리 기반 RecentSearchStore 페이크 (실제 구현과 동일하게 최신 우선·중복 제거·최대 8개). */
class FakeRecentSearchStore : RecentSearchStore {
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _recent.value = (listOf(q) + _recent.value.filter { it != q }).take(8)
    }

    override fun remove(query: String) {
        _recent.value = _recent.value.filter { it != query }
    }

    override fun clear() { _recent.value = emptyList() }
}
