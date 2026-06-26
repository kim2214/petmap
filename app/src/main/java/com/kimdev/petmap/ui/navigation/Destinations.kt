package com.kimdev.petmap.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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

/** 하단 탭 항목 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    MAP(Routes.MAP, "지도", Icons.Filled.Map),
    LIST(Routes.LIST, "목록", Icons.AutoMirrored.Filled.List),
    FAVORITE(Routes.FAVORITE, "즐겨찾기", Icons.Filled.FavoriteBorder),
    SETTINGS(Routes.SETTINGS, "설정", Icons.Filled.Settings),
}
