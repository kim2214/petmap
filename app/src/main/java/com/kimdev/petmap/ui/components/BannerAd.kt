package com.kimdev.petmap.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kimdev.petmap.BuildConfig

/**
 * 화면 하단 적응형 배너 광고. 광고 단위 ID 는 BuildConfig(ADMOB_BANNER_UNIT_ID)에서 주입.
 * 기본값은 Google 공식 테스트 ID 라 개발 중엔 항상 테스트 광고가 표시된다.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(adaptiveAdSize(context))
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        // 화면(탭)을 벗어날 때 AdView 를 해제해 누수를 막는다.
        onRelease = { it.destroy() },
    )
}

private fun adaptiveAdSize(context: Context): AdSize {
    val metrics = context.resources.displayMetrics
    val widthDp = (metrics.widthPixels / metrics.density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
}
