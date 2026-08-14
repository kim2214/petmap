package com.kimdev.petmap.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.kimdev.petmap.R

/** 앱 라우트 정의 */
object Routes {
    const val MAP = "map"
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

    /** 상세 딥링크 패턴 (AndroidManifest 의 intent-filter 와 일치해야 함) */
    const val DETAIL_DEEP_LINK_PATTERN = "petmap://place/{$DETAIL_ARG_ID}"

    /** 공유 텍스트에 넣는 상세 딥링크. */
    fun detailDeepLink(placeId: String) = "petmap://place/${Uri.encode(placeId)}"
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
