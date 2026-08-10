package com.kimdev.petmap.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun PetMapNavHost(
    navController: NavHostController,
    tabReselects: Flow<String> = emptyFlow(),
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
        // launchSingleTop 은 "같은 라우트"의 중복만 막으므로, 서로 다른 두 카드를 같은 프레임에
        // 탭하면 상세가 2장 쌓인다 → 이미 상세가 떠 있으면 추가 push 를 막는다.
        val openDetail: (String) -> Unit = { id ->
            if (navController.currentDestination?.route != Routes.DETAIL_PATTERN) {
                navController.navigate(Routes.detail(id)) { launchSingleTop = true }
            }
        }

        composable(Routes.MAP) {
            val reselects = remember { tabReselects.filter { it == Routes.MAP }.map { } }
            MapScreen(onPlaceClick = openDetail, reselects = reselects)
        }
        composable(Routes.LIST) {
            val reselects = remember { tabReselects.filter { it == Routes.LIST }.map { } }
            ListScreen(onPlaceClick = openDetail, reselects = reselects)
        }
        composable(Routes.FAVORITE) {
            val reselects = remember { tabReselects.filter { it == Routes.FAVORITE }.map { } }
            FavoriteScreen(
                onPlaceClick = openDetail,
                reselects = reselects,
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
                    // 지도 화면을 상세 "위에" 쌓는다 (포커스는 MapFocusBus 가 전달).
                    // popUpTo(start)로 백스택을 비우면 위치만 확인하고 돌아올 목록/상세가
                    // 사라져 뒤로가기가 곧장 앱 종료가 된다.
                    navController.navigate(Routes.MAP) { launchSingleTop = true }
                },
            )
        }
    }
}
