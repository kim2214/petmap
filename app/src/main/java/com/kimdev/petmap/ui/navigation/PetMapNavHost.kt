package com.kimdev.petmap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kimdev.petmap.ui.detail.DetailScreen
import com.kimdev.petmap.ui.favorite.FavoriteScreen
import com.kimdev.petmap.ui.list.ListScreen
import com.kimdev.petmap.ui.map.MapScreen
import com.kimdev.petmap.ui.settings.LicensesScreen
import com.kimdev.petmap.ui.settings.SettingsScreen

@Composable
fun PetMapNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAP,
        modifier = modifier,
    ) {
        val openDetail: (String) -> Unit = { id -> navController.navigate(Routes.detail(id)) }

        composable(Routes.MAP) {
            MapScreen(onPlaceClick = openDetail)
        }
        composable(Routes.LIST) {
            ListScreen(onPlaceClick = openDetail)
        }
        composable(Routes.FAVORITE) {
            FavoriteScreen(
                onPlaceClick = openDetail,
                onExplore = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenLicenses = { navController.navigate(Routes.LICENSES) })
        }
        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.DETAIL_ARG_ID) { type = NavType.StringType }),
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onShowOnMap = {
                    // 지도 탭으로 전환 (포커스는 MapFocusBus 가 전달)
                    navController.navigate(Routes.MAP) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
