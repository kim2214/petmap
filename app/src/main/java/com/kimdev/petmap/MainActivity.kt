package com.kimdev.petmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kimdev.petmap.core.ads.AdsConsent
import com.kimdev.petmap.core.review.InAppReview
import com.kimdev.petmap.ui.navigation.PetMapNavHost
import com.kimdev.petmap.ui.navigation.TopLevelDestination
import com.kimdev.petmap.ui.theme.PetMapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 광고 동의(UMP) 수집 → 동의되면 광고 SDK 초기화
        AdsConsent.gather(this)
        // 일정 실행 횟수 이상이면 인앱 리뷰 요청 (Play 설치 빌드에서만 실제 노출)
        InAppReview.maybeAsk(this)
        setContent {
            PetMapTheme {
                PetMapApp()
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
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
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
