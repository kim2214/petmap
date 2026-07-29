package com.kimdev.petmap.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 최근 검색어 저장(기기 로컬, 최대 8개, 최신 우선·중복 제거). */
interface RecentSearchStore {
    val recent: StateFlow<List<String>>
    fun add(query: String)
    fun remove(query: String)
    fun clear()
}

/**
 * SharedPreferences 기반 구현.
 *
 * 로드는 생성 시 동기로 한다. 비동기로 바꿨다가 (a) 로드 완료가 그 사이 사용자가 추가한 항목을
 * 덮어쓰고 (b) 동시 저장이 서로 순서를 어긋나게 하는 문제가 나왔다. 항목 8개짜리 작은
 * 프리퍼런스 하나라 읽기 비용이 작아, 동시성 복잡도를 들이는 것보다 동기 로드가 낫다.
 * 쓰기는 `apply()` 라 디스크 IO 가 백그라운드에서 처리된다.
 */
@Singleton
class RecentSearchStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : RecentSearchStore {
    private val prefs = context.getSharedPreferences("recent_search", Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(load())
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        // update {}: 읽기-쓰기가 원자적이라 동시 호출에도 항목이 유실되지 않는다
        _recent.update { (listOf(q) + it.filter { prev -> prev != q }).take(MAX) }
        persist()
    }

    override fun remove(query: String) {
        _recent.update { list -> list.filter { it != query } }
        persist()
    }

    override fun clear() {
        _recent.value = emptyList()
        persist()
    }

    private fun load(): List<String> =
        prefs.getStringSet(KEY, emptySet())
            ?.sortedBy { it.substringBefore(SEP).toIntOrNull() ?: Int.MAX_VALUE }
            ?.map { it.substringAfter(SEP) }
            ?: emptyList()

    /**
     * StringSet 으로 저장한다. 이전 구현은 "\n" join 이라 검색어에 개행이 섞이면 한 항목이
     * 여러 개로 쪼개졌다. Set 은 순서를 보장하지 않으므로 "인덱스 구분자 검색어" 형태로 저장한다.
     */
    private fun persist() {
        val encoded = _recent.value.mapIndexed { i, q -> "$i$SEP$q" }.toSet()
        prefs.edit { putStringSet(KEY, encoded) }
    }

    companion object {
        // 저장 포맷이 바뀌었으므로 키를 분리한다(구버전 값은 무시되고 새로 쌓인다).
        private const val KEY = "queries_v2"
        private const val SEP = " "
        private const val MAX = 8
    }
}
