package com.kimdev.petmap

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.ads.AdsConsent
import com.kimdev.petmap.core.review.InAppReview
import com.kimdev.petmap.data.local.ThemeMode
import com.kimdev.petmap.data.local.ThemeStore
import com.kimdev.petmap.ui.components.BannerAd
import com.kimdev.petmap.ui.onboarding.OnboardingPrefs
import com.kimdev.petmap.ui.onboarding.OnboardingScreen
import javax.inject.Inject
import com.kimdev.petmap.ui.navigation.PetMapNavHost
import com.kimdev.petmap.ui.navigation.TopLevelDestination
import com.kimdev.petmap.ui.theme.PetMapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 광고 동의(UMP) 수집 → 동의되면 광고 SDK 초기화
        AdsConsent.gather(this)
        // 일정 실행 횟수 이상이면 인앱 리뷰 요청 (Play 설치 빌드에서만 실제 노출).
        // 회전·테마 전환 등 구성 변경에 의한 Activity 재생성은 실행 횟수로 세지 않는다.
        if (savedInstanceState == null) InAppReview.maybeAsk(this)
        setContent {
            val themeMode by themeStore.mode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 시스템바를 투명하게 두되, 아이콘 명암은 시스템이 아닌 "앱 테마"를 따르게 한다.
            // (인앱에서 라이트/다크를 강제해도 상태바 아이콘이 배경과 항상 대비됨)
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }
            PetMapTheme(darkTheme = darkTheme) {
                var showOnboarding by rememberSaveable {
                    mutableStateOf(!OnboardingPrefs.isCompleted(this@MainActivity))
                }
                if (showOnboarding) {
                    OnboardingScreen(onFinish = {
                        OnboardingPrefs.setCompleted(this@MainActivity)
                        showOnboarding = false
                    })
                } else {
                    PetMapApp()
                }
            }
        }
    }
}

@Composable
private fun PetMapApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 상세 화면 등에서는 하단탭을 숨긴다.
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        // edge-to-edge(decorFitsSystemWindows=false)라 키보드가 콘텐츠·배너·하단탭을 덮는다.
        // Scaffold 전체를 올려 검색 중에도 목록과 하단탭이 가려지지 않게 한다.
        modifier = Modifier.fillMaxSize().imePadding(),
        // 상단 시스템바 인셋을 콘텐츠에 적용하지 않음 → 지도가 상태바 밑까지 풀블리드.
        // 각 화면이 필요 시 statusBarsPadding 으로 직접 처리한다.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // 배너를 화면마다 두지 않고 여기 한 곳에 둔다 → 탭을 옮겨도 AdView 가
                    // 파괴·재생성되지 않아 탭 전환마다 새 광고를 요청하지 않는다.
                    BannerAd()
                    // 콘텐츠와 바를 또렷하게 분리하는 얇은 구분선
                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TopLevelDestination.entries.forEach { dest ->
                            val selected =
                                currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(dest.iconRes),
                                        contentDescription = stringResource(dest.labelRes),
                                        tint = Color.Unspecified, // 컬러 일러스트 원본 색 유지
                                        modifier = Modifier
                                            .size(26.dp)
                                            .alpha(if (selected) 1f else 0.5f), // 비선택은 흐리게 de-emphasis
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        PetMapNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
