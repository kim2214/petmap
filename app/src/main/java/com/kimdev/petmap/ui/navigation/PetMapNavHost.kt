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
import androidx.navigation.navDeepLink
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
        // "지도에서 보기": 상세 위에 push 되는 포커스 지도. 탭이 아니므로 하단탭이 숨고 재선택 이벤트도 없다.
        composable(
            route = Routes.MAP_FOCUS_PATTERN,
            arguments = listOf(navArgument(Routes.MAP_ARG_FOCUS) { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
        ) {
            MapScreen(onPlaceClick = openDetail)
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
            // 공유 링크로 진입 (HTTPS App Link + 구버전 petmap:// 스킴). 콜드 스타트는 NavHost 가
            // Activity 인텐트를 자동 처리하고, 실행 중 수신은 MainActivity 의 onNewIntent 리스너가 넘긴다.
            deepLinks = listOf(
                navDeepLink { uriPattern = Routes.DETAIL_APP_LINK_PATTERN },
                navDeepLink { uriPattern = Routes.DETAIL_DEEP_LINK_PATTERN },
            ),
            // 상세는 오른쪽에서 밀려 들어오는 push 전환
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onShowOnMap = { placeId ->
                    // 포커스 대상을 라우트 인자로 실어 지도를 상세 위에 push 한다 → 뒤로가기로 상세 복귀.
                    // (새 지도 ViewModel 이 자기 인자를 읽으므로 기존 지도 탭과 경쟁하지 않는다)
                    navController.navigate(Routes.mapFocused(placeId)) { launchSingleTop = true }
                },
            )
        }
    }
}
