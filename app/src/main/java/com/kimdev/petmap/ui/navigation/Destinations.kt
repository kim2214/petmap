package com.kimdev.petmap.ui.navigation

import androidx.annotation.DrawableRes
import com.kimdev.petmap.R

/** 앱 라우트 정의 */
object Routes {
    const val MAP = "map"
    const val LIST = "list"
    const val FAVORITE = "favorite"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val ONBOARDING = "onboarding"
    const val DETAIL = "detail"
    const val DETAIL_ARG_ID = "placeId"
    const val DETAIL_PATTERN = "$DETAIL/{$DETAIL_ARG_ID}"

    fun detail(placeId: String) = "$DETAIL/$placeId"
}

/** 하단 탭 항목. 컬러 일러스트 아이콘(브랜드 커스텀)을 사용. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    MAP(Routes.MAP, "지도", R.drawable.ic_nav_map),
    LIST(Routes.LIST, "목록", R.drawable.ic_nav_list),
    FAVORITE(Routes.FAVORITE, "즐겨찾기", R.drawable.ic_nav_favorite),
    SETTINGS(Routes.SETTINGS, "설정", R.drawable.ic_nav_settings),
}
