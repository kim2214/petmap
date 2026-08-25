package com.kimdev.petmap.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.kimdev.petmap.R

/** 앱 라우트 정의 */
object Routes {
    const val MAP = "map"
    /**
     * "지도에서 보기" 포커스 대상. 지도를 새 백스택 엔트리로 push 하면서 라우트 인자로 전달한다 —
     * 앱 스코프 버스로 넘기면 살아 있는 기존 지도 ViewModel 이 요청을 가로채 새 지도는 포커스되지
     * 않았다(그래서 popUpTo 로 상세를 날려야 했고 뒤로가기로 상세 복귀가 불가능했다).
     */
    const val MAP_ARG_FOCUS = "focus"
    const val MAP_PATTERN = "$MAP?$MAP_ARG_FOCUS={$MAP_ARG_FOCUS}"
    const val LIST = "list"
    const val FAVORITE = "favorite"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val ONBOARDING = "onboarding"
    const val DETAIL = "detail"
    const val DETAIL_ARG_ID = "placeId"
    const val DETAIL_PATTERN = "$DETAIL/{$DETAIL_ARG_ID}"

    /**
     * placeId 는 `이름_위도_경도` 형태로 데이터에서 생성되므로 `/`·`?`·`#` 이 섞일 수 있다.
     * 인코딩하지 않으면 경로 패턴 매칭이 깨져 navigate 가 IllegalArgumentException 으로 크래시한다.
     * (Navigation 이 인자를 읽을 때 디코딩하므로 DetailViewModel 은 원본 문자열을 받는다)
     */
    fun detail(placeId: String) = "$DETAIL/${Uri.encode(placeId)}"

    /** 특정 장소를 비추는 지도 (상세 위에 push — 뒤로가기로 상세 복귀) */
    fun mapFocused(placeId: String) = "$MAP?$MAP_ARG_FOCUS=${Uri.encode(placeId)}"

    /**
     * 구버전 공유 링크(커스텀 스킴). 이미 뿌려진 링크가 계속 열리도록 유지한다
     * (AndroidManifest 의 intent-filter 와 일치해야 함).
     */
    const val DETAIL_DEEP_LINK_PATTERN = "petmap://place/{$DETAIL_ARG_ID}"

    /**
     * HTTPS App Link. 미설치 기기에서도 브라우저로 안내 페이지가 열리고, 설치 기기에서는
     * assetlinks.json(kim2214.github.io/.well-known) 검증을 거쳐 앱이 바로 열린다.
     * 호스트·경로는 AndroidManifest 의 autoVerify intent-filter 및 GitHub Pages 의
     * petmap/place.html 과 일치해야 한다. 장소 id 는 경로가 아니라 쿼리로 싣는다 —
     * GitHub Pages 는 정적이라 임의 경로를 서빙할 수 없기 때문.
     */
    const val APP_LINK_HOST = "kim2214.github.io"
    const val APP_LINK_PATH = "/petmap/place"
    const val DETAIL_APP_LINK_PATTERN = "https://$APP_LINK_HOST$APP_LINK_PATH?id={$DETAIL_ARG_ID}"

    /** 공유 텍스트에 넣는 상세 링크 (HTTPS App Link). */
    fun detailDeepLink(placeId: String) = "https://$APP_LINK_HOST$APP_LINK_PATH?id=${Uri.encode(placeId)}"
}

/** 하단 탭 항목. 컬러 일러스트 아이콘(브랜드 커스텀)을 사용. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    MAP(Routes.MAP, R.string.nav_map, R.drawable.ic_nav_map),
    LIST(Routes.LIST, R.string.nav_list, R.drawable.ic_nav_list),
    FAVORITE(Routes.FAVORITE, R.string.nav_favorite, R.drawable.ic_nav_favorite),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_nav_settings),
}
