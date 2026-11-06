package com.kimdev.petmap.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimdev.petmap.HandleIncomingPlaceLinks
import com.kimdev.petmap.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실행 중 받은 장소 링크 처리([HandleIncomingPlaceLinks])의 계약.
 *
 * 앱에선 링크 리스너가 화면 구성 직후 등록되지만 NavHost(그래프)는 Scaffold 안쪽이라 첫 레이아웃에서야 생긴다.
 * 잠금 화면 뒤에서 실행돼 그려지지 않은 Activity 에 링크가 오면 그래프 없이 navigate 해 죽었다(1.10, 실기기 발견).
 * 에뮬레이터에선 그 상태(구성 O·레이아웃 X)를 안정적으로 만들 수 없어, 그래프를 늦게 붙이는 화면으로 조건을 직접 만든다.
 */
@RunWith(AndroidJUnit4::class)
class IncomingPlaceLinksTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var showHost by mutableStateOf(false)
    private lateinit var nav: NavHostController

    private fun setUpScreen() {
        compose.setContent {
            nav = rememberNavController()
            HandleIncomingPlaceLinks(compose.activity, nav)
            if (showHost) {
                NavHost(nav, startDestination = "home") {
                    composable("home") { Text("home") }
                    composable(
                        Routes.DETAIL_PATTERN,
                        arguments = listOf(navArgument(Routes.DETAIL_ARG_ID) { type = NavType.StringType }),
                    ) { entry -> Text("detail:${entry.arguments?.getString(Routes.DETAIL_ARG_ID)}") }
                }
            }
        }
    }

    private fun deliver(placeId: String) = compose.runOnUiThread {
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
            compose.activity,
            Intent(Intent.ACTION_VIEW, Uri.parse(Routes.detailDeepLink(placeId))),
        )
    }

    @Test
    fun linkBeforeGraphIsSet_isKept_andOpenedOnceGraphExists() {
        setUpScreen()
        compose.waitForIdle()

        deliver(PLACE_ID) // 그래프 없음 — 수정 전에는 여기서 IllegalArgumentException 으로 죽었다
        compose.waitForIdle()
        assertNull(nav.currentBackStackEntry)

        showHost = true // 첫 레이아웃에 해당: 그래프가 생긴다
        compose.waitForIdle()
        compose.onNodeWithText("detail:$PLACE_ID").assertExists()
        assertEquals(listOf("home", Routes.DETAIL_PATTERN), nav.currentBackStack.value.mapNotNull { it.destination.route })
    }

    @Test
    fun linkWithGraph_pushesDetailOnce_evenIfSameLinkArrivesTwice() {
        showHost = true
        setUpScreen()
        compose.waitForIdle()

        deliver(PLACE_ID)
        compose.waitForIdle()
        deliver(PLACE_ID) // 같은 링크를 다시 눌러도 상세가 두 장 쌓이지 않는다
        compose.waitForIdle()

        compose.onNodeWithText("detail:$PLACE_ID").assertExists()
        assertEquals(listOf("home", Routes.DETAIL_PATTERN), nav.currentBackStack.value.mapNotNull { it.destination.route })
    }

    @Test
    fun otherLinks_areIgnored() {
        showHost = true
        setUpScreen()
        compose.waitForIdle()

        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
                compose.activity, Intent(Intent.ACTION_VIEW, Uri.parse("https://kim2214.github.io/privacy-policy.html")),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("home").assertExists()
    }

    private companion object {
        const val PLACE_ID = "행복카페_37.5_127.0"
    }
}
