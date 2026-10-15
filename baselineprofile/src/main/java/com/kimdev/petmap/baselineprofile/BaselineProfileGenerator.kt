package com.kimdev.petmap.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Baseline Profile 생성기.
 *
 * - [startup]: 콜드 스타트 → 첫 지도 렌더링까지만. startup-prof.txt 로도 들어가 DEX 레이아웃 최적화
 *   (시작 코드를 첫 dex 에 모음)를 받는다. 여기에 화면 순회까지 넣으면 거의 앱 전체가 "시작 코드"가 돼
 *   최적화가 무의미해진다(이전엔 startup-prof == baseline-prof 였다).
 * - [journey]: 지도 → 목록 스크롤·상세 → 즐겨찾기 → 설정. baseline-prof.txt 에만 들어가 AOT 컴파일 대상이 된다.
 *
 * 실행: 에뮬레이터/기기(API 33+) 연결 후 `./gradlew :app:generateBaselineProfile`
 * 결과: app/src/release/generated/baselineProfiles/{baseline-prof,startup-prof}.txt (리포에 커밋)
 *
 * 화면 탐색은 문자열 리소스 텍스트(한국어)에 의존한다 — 라벨을 바꾸면 여기도 맞춘다.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            coldStartToMap()
        }
    }

    @Test
    fun journey() {
        rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
            coldStartToMap()
            openTab("목록")
            scrollList()
            openTab("즐겨찾기")
            device.waitForIdle()
            openTab("설정")
            device.waitForIdle()
            openTab("지도")
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.coldStartToMap() {
        pressHome()
        // 지도 첫 진입이 위치 권한을 요청한다 → 시스템 다이얼로그가 탐색을 막지 않도록 미리 허용
        grantLocationPermissions()
        startActivityAndWait()
        skipOnboardingIfShown()
        dismissPermissionDialogIfShown()
        // 지도: 에셋 DB 복사(첫 실행) + 첫 조회 + 마커 렌더링이 끝날 때까지
        device.waitForIdle()
        device.wait(Until.gone(By.text("장소 데이터를 준비하고 있어요…")), 30_000)
        Thread.sleep(2_000)
    }

    private fun MacrobenchmarkScope.grantLocationPermissions() {
        device.executeShellCommand("pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $PACKAGE android.permission.ACCESS_COARSE_LOCATION")
    }

    private fun MacrobenchmarkScope.skipOnboardingIfShown() {
        // "건너뛰기" 텍스트 노드는 클릭 불가지만 중심 탭이 부모 버튼에 전달된다
        val skip = device.wait(Until.findObject(By.text("건너뛰기")), 5_000) ?: return
        skip.click()
        device.waitForIdle()
    }

    /**
     * pm grant 가 안 먹는 환경(런타임 권한 재요청 등)을 위한 폴백. 시스템 다이얼로그 문구는
     * 로케일·따옴표(’) 가 제각각이라 정규식으로 느슨하게 맞춘다.
     */
    private fun MacrobenchmarkScope.dismissPermissionDialogIfShown() {
        val allow = device.wait(
            Until.findObject(By.text(Pattern.compile("While using the app|앱 사용 중에만 허용"))),
            3_000,
        ) ?: device.findObject(By.text(Pattern.compile("Don.t allow|허용 안함")))
        allow?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openTab(label: String) {
        // 하단 내비게이션 항목은 라벨 텍스트를 가진 클릭 가능한 노드
        val tab = device.wait(Until.findObject(By.text(label).clickable(true)), 5_000)
            ?: device.wait(Until.findObject(By.text(label)), 5_000)
            ?: return
        tab.click()
        device.waitForIdle()
        Thread.sleep(1_000)
    }

    private fun MacrobenchmarkScope.scrollList() {
        // 목록 스켈레톤 → 카드 렌더링 대기. 스크롤 가능한 노드 중 가장 큰 것이 세로 목록이다
        // (칩 행은 가로 HorizontalScrollView 라 먼저 잡히면 안 된다)
        Thread.sleep(1_500)
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        val list = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: return
        list.setGestureMargin(device.displayWidth / 5)
        repeat(2) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        list.fling(Direction.UP)
        device.waitForIdle()
        openFirstCardAndBack(list)
    }

    /** 첫 카드 → 상세 화면(히어로 헤더·운영시간 표·미니맵) 렌더링 경로 수집 후 복귀 */
    private fun MacrobenchmarkScope.openFirstCardAndBack(list: androidx.test.uiautomator.UiObject2) {
        val first = list.children.firstOrNull() ?: return
        first.click()
        device.waitForIdle()
        Thread.sleep(2_500)
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(500)
    }

    private companion object {
        const val PACKAGE = "com.kimdev.petmap"
    }
}
