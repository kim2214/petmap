package com.kimdev.petmap.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 최근 검색어 저장(기기 로컬, 최대 8개, 최신 우선·중복 제거). */
@Singleton
class RecentSearchStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("recent_search", Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        save((listOf(q) + _recent.value.filter { it != q }).take(MAX))
    }

    fun remove(query: String) = save(_recent.value.filter { it != query })

    fun clear() = save(emptyList())

    private fun load(): List<String> =
        prefs.getString(KEY, "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
        _recent.value = list
    }

    companion object {
        private const val KEY = "queries"
        private const val MAX = 8
    }
}
