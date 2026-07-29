package com.kimdev.petmap.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.kimdev.petmap.BuildConfig
import com.kimdev.petmap.core.ads.AdsConsent
import kotlinx.coroutines.delay

/** 로드 실패 재시도 상한과 기본 지연(선형 백오프: 지연 × 시도 횟수) */
private const val MAX_RETRY = 3
private const val RETRY_DELAY_MS = 5_000L

/**
 * 화면 하단 적응형 배너 광고. 광고 단위 ID 는 BuildConfig(ADMOB_BANNER_UNIT_ID)에서 주입.
 * 기본값은 Google 공식 테스트 ID 라 개발 중엔 항상 테스트 광고가 표시된다.
 *
 * 하단탭 Scaffold 에 한 번만 배치해 탭을 옮겨도 AdView 가 유지된다
 * (화면마다 두면 탭 전환마다 파괴·재요청되어 노출/요청 비율이 나빠진다).
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // UMP 동의 후 MobileAds 초기화가 끝나야 로드가 성공한다. 초기화 전에 요청하면 실패하고
    // 재시도가 없으면 첫 세션 내내 빈 배너가 된다.
    val adsReady by AdsConsent.adsReady.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 화면 전체 폭이 아니라 실제 윈도우 폭(멀티윈도우·폴더블 대응). Configuration.screenWidthDp 는
    // 이런 환경에서 부정확할 수 있어 containerSize(px) → dp 변환을 쓴다.
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }.value.toInt()
    // 25.x 에서 getCurrentOrientation... 은 deprecated 지만, 대체인 getLarge*(더 큰 배너)는
    // 화면 점유가 달라지는 별개 상품이라 의도적으로 유지한다. 전환은 수익/UX 검토 후 결정할 것.
    @Suppress("DEPRECATION")
    val adSize = remember(windowWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, windowWidthDp)
    }
    val adView = remember(adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
        }
    }
    var attempt by remember(adView) { mutableIntStateOf(0) }

    DisposableEffect(adView) {
        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                if (attempt < MAX_RETRY) attempt++
            }
        }
        onDispose { adView.destroy() }
    }

    // 앱이 백그라운드일 때 배너 자동 갱신을 멈춘다(노출되지 않는 광고 요청 방지).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(adsReady, attempt, adView) {
        if (!adsReady) return@LaunchedEffect
        if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
        adView.loadAd(AdRequest.Builder().build())
    }

    // 로드 성공 여부와 무관하게 높이를 예약 → 광고가 늦게 채워질 때 목록이 밀리는 점프 방지
    Box(modifier = modifier.fillMaxWidth().height(adSize.height.dp)) {
        if (adsReady) {
            AndroidView(modifier = Modifier.fillMaxWidth(), factory = { adView })
        }
    }
}
