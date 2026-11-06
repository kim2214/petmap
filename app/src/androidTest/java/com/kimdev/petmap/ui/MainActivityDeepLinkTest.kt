package com.kimdev.petmap.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimdev.petmap.MainActivity
import com.kimdev.petmap.ui.navigation.Routes
import com.kimdev.petmap.ui.onboarding.OnboardingPrefs
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 앱이 이미 떠 있을 때 받은 공유 링크(onNewIntent) 처리 — 실제 MainActivity·DB·내비게이션으로 검증한다.
 * (그래프가 설정되기 전에 링크가 오는 경우는 IncomingPlaceLinksTest 가 조건을 직접 만들어 검증한다)
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 첫 실행 요소(온보딩·위치 권한 다이얼로그)가 화면을 가리지 않게 한다
        OnboardingPrefs.setCompleted(context)
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
        }
    }

    @Test
    fun linkWhileVisible_pushesDetailOnSameActivity_backReturns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(MAP_TAB)
            var before: MainActivity? = null
            scenario.onActivity { before = it; deliverLink(it, PLACE_ID) }

            waitForText(PLACE_NAME)
            scenario.onActivity { assertSame("Activity 가 재시작됐다", before, it) }

            Espresso.pressBack()
            waitForText(MAP_TAB)
            compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(PLACE_NAME).fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun legacyIdLink_opensSamePlaceUnderCurrentId() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(MAP_TAB)
            // 법인 표기 정제(v9) 전에 공유된 링크의 id
            scenario.onActivity { deliverLink(it, "(주)${PLACE_NAME_CORP}_$PLACE_COORDS") }
            waitForText(PLACE_NAME_CORP)
        }
    }

    /** 브라우저·메신저에서 링크를 누른 것과 같은 경로(singleTask → onNewIntent)로 전달한다. 메인 스레드에서 부른다 */
    private fun deliverLink(activity: MainActivity, placeId: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Routes.detailDeepLink(placeId))).setPackage(context.packageName)
        instrumentation.callActivityOnNewIntent(activity, intent)
    }

    private fun waitForText(text: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val TIMEOUT = 20_000L
        const val MAP_TAB = "지도"
        // 에셋 DB 의 실제 장소 (첫 실행에 createFromAsset 으로 복사된다)
        const val PLACE_NAME = "24시 SNC 동물메디컬센터"
        const val PLACE_ID = "24시 SNC 동물메디컬센터_37.4974084_127.038878"
        const val PLACE_NAME_CORP = "커피빈코리아 송파파크하비오점"
        const val PLACE_COORDS = "37.4805094_127.1232611"
    }
}
