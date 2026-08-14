package com.kimdev.petmap.ui.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kimdev.petmap.R
import com.kimdev.petmap.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

/** 첫 실행 여부 플래그 (SharedPreferences) */
object OnboardingPrefs {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "completed"

    fun isCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun setCompleted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_DONE, true) }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    // 다크 배경에선 중간 명도 원색의 대비가 떨어지므로 밝힌 변형을 함께 정의한다.
    // (라이트 값은 테마 primary·카테고리 팔레트와 맞춘 색)
    val accentLight: Color,
    val accentDark: Color,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Map,
        titleRes = R.string.onboarding_page1_title,
        descRes = R.string.onboarding_page1_desc,
        accentLight = Color(0xFF22A75A),
        accentDark = Color(0xFF6FDB91),
    ),
    OnboardingPage(
        icon = Icons.Filled.FilterAlt,
        titleRes = R.string.onboarding_page2_title,
        descRes = R.string.onboarding_page2_desc,
        accentLight = Color(0xFF3D8BD4),
        accentDark = Color(0xFF85BCEB),
    ),
    OnboardingPage(
        icon = Icons.Filled.Pets,
        titleRes = R.string.onboarding_page3_title,
        descRes = R.string.onboarding_page3_desc,
        accentLight = Color(0xFFF2913C),
        accentDark = Color(0xFFF6B678),
    ),
    // 위치 권한 사전 안내: 지도 화면에서 맥락 없이 튀어나오는 것보다 이유를 먼저 설명하면
    // 허용률이 올라간다. 실제 요청은 마지막 "시작하기" 탭에서 이뤄진다.
    OnboardingPage(
        icon = Icons.Filled.MyLocation,
        titleRes = R.string.onboarding_page4_title,
        descRes = R.string.onboarding_page4_desc,
        accentLight = Color(0xFF18A89B),
        accentDark = Color(0xFF82D6CC),
    ),
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    // currentPage 를 직접 읽으면 스와이프 매 프레임 재구성 → 마지막 페이지 진입 시에만 갱신.
    val isLast by remember { derivedStateOf { pagerState.currentPage == pages.lastIndex } }

    // 마지막 페이지(위치 안내)의 "시작하기"에서 권한을 요청한다.
    // 허용/거부와 무관하게 온보딩은 종료 — 거부해도 앱은 정상 동작한다(거리 기능만 제한).
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ) { onFinish() }

    // 뒤로가기: 이전 페이지로. 첫 페이지에선 건너뛰기와 동일하게 완료 처리 —
    // 첫 실행에서 앱만 종료되고 완료 마킹이 안 돼 다음 실행에 온보딩이 또 뜨는 것을 막는다.
    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onFinish()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        // 건너뛰기 — 마지막 페이지에선 "시작하기"와 CTA 가 중복되므로 숨긴다
        // (alpha 로 숨겨 자리는 유지 → 페이저 높이가 튀지 않고, disabled 로 탭·포커스도 차단)
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onFinish,
                enabled = !isLast,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .alpha(if (isLast) 0f else 1f),
            ) { Text(stringResource(R.string.onboarding_skip)) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            PageContent(pages[page])
        }

        // 페이지 인디케이터
        val pageDesc = stringResource(
            R.string.onboarding_page_indicator_format,
            pagerState.currentPage + 1,
            pages.size,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(vertical = 24.dp)
                .align(Alignment.CenterHorizontally)
                // 점들은 장식이므로 진행도를 텍스트로 대신 읽어준다
                .semantics { contentDescription = pageDesc },
        ) {
            repeat(pages.size) { i ->
                val selected = pagerState.currentPage == i
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    label = "dotColor",
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        Button(
            onClick = {
                when {
                    !isLast -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    locationPermissions.allPermissionsGranted -> onFinish()
                    else -> locationPermissions.launchMultiplePermissionRequest() // 콜백에서 onFinish
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                // 고정 height 는 큰 글꼴 배율에서 텍스트가 잘린다
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    val accent = if (LocalIsDarkTheme.current) page.accentDark else page.accentLight
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                // 상세 화면 히어로 헤더와 같은 위→아래 옅어지는 그라데이션 톤
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.08f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(80.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(page.descRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
