package com.kimdev.petmap.ui.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.kimdev.petmap.R
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
    val accent: Color,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Map,
        titleRes = R.string.onboarding_page1_title,
        descRes = R.string.onboarding_page1_desc,
        accent = Color(0xFF22A75A),
    ),
    OnboardingPage(
        icon = Icons.Filled.FilterAlt,
        titleRes = R.string.onboarding_page2_title,
        descRes = R.string.onboarding_page2_desc,
        accent = Color(0xFF3D8BD4),
    ),
    OnboardingPage(
        icon = Icons.Filled.Pets,
        titleRes = R.string.onboarding_page3_title,
        descRes = R.string.onboarding_page3_desc,
        accent = Color(0xFFF2913C),
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    // currentPage 를 직접 읽으면 스와이프 매 프레임 재구성 → 마지막 페이지 진입 시에만 갱신.
    val isLast by remember { derivedStateOf { pagerState.currentPage == pages.lastIndex } }

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
        // 건너뛰기
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.CenterEnd),
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(vertical = 24.dp)
                .align(Alignment.CenterHorizontally),
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
                if (isLast) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(page.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = page.accent,
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
