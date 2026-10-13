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

/** 최근 본 장소 ID (기기 로컬, 최대 [MAX]개, 최신 우선·중복 제거). */
interface RecentPlaceStore {
    val recent: StateFlow<List<String>>
    fun add(placeId: String)
    fun remove(placeId: String)
    fun clear()

    companion object {
        const val MAX = 30
    }
}

/**
 * SharedPreferences 기반 구현 ([RecentSearchStoreImpl] 과 같은 이유로 동기 로드 + `apply()` 쓰기).
 *
 * Room 이 아니라 프리퍼런스에 두는 이유: places 와 같은 DB 에 테이블을 추가하면 identity_hash 가 바뀌어
 * 에셋 재생성·DB 버전 업이 필요하고, 파괴적 재생성 때 함께 지워진다. ID 만 들고 있다가 표시 시점에
 * 장소를 조회하므로, 데이터 갱신으로 사라진 장소는 자연스럽게 목록에서 빠진다.
 */
@Singleton
class RecentPlaceStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : RecentPlaceStore {
    private val prefs = context.getSharedPreferences("recent_place", Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(load())
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun add(placeId: String) {
        if (placeId.isBlank()) return
        // 같은 장소를 다시 보면 맨 앞으로 올린다
        _recent.update { (listOf(placeId) + it.filter { prev -> prev != placeId }).take(RecentPlaceStore.MAX) }
        persist()
    }

    override fun remove(placeId: String) {
        _recent.update { list -> list.filter { it != placeId } }
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

    /** StringSet 은 순서를 보장하지 않으므로 "인덱스 구분자 ID" 로 저장한다 */
    private fun persist() {
        val encoded = _recent.value.mapIndexed { i, id -> "$i$SEP$id" }.toSet()
        prefs.edit { putStringSet(KEY, encoded) }
    }

    private companion object {
        const val KEY = "place_ids"
        const val SEP = " "
    }
}
