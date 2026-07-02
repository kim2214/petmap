package com.kimdev.petmap.ui.common

import com.kimdev.petmap.domain.model.PlaceCategory

/**
 * SavedStateHandle 는 Bundle 로 직렬화되므로 카테고리 Set 을 이름 리스트로 저장/복원한다.
 * (알 수 없는 이름은 무시 — enum 이 바뀌어도 크래시 없이 폴백)
 */
object SavedFilters {

    fun categoriesToNames(categories: Set<PlaceCategory>): ArrayList<String> =
        ArrayList(categories.map { it.name })

    fun namesToCategories(names: List<String>?): Set<PlaceCategory> =
        names?.mapNotNull { runCatching { PlaceCategory.valueOf(it) }.getOrNull() }?.toSet()
            ?: emptySet()
}
