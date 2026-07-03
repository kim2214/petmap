package com.kimdev.petmap.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.kimdev.petmap.ui.onboarding.OnboardingScreen
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
        // 탭/일반 화면 기본 전환: 부드러운 페이드
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
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
            SettingsScreen(
                onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinish = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.DETAIL_ARG_ID) { type = NavType.StringType }),
            // 상세는 오른쪽에서 밀려 들어오는 push 전환
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
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
