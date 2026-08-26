package com.kimdev.petmap.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 목록형 탭 화면(목록·즐겨찾기)의 공통 골격.
 *
 * 큰 제목 + [header](검색창·필터 칩) 를 담은 헤더 Surface 와 그 아래 본문 Box 로 구성되며,
 * 본문이 스크롤되기 시작하면 제목을 접어 결과 영역을 넓히고 헤더에 구분 그림자를 띄운다.
 * 스낵바는 본문 하단 중앙에 띄운다. 두 화면이 각자 복제하던 구조를 한 곳에 둔다.
 *
 * @param listState 접힘 판정용 본문 목록 상태 (첫 항목이 조금이라도 밀리면 "스크롤됨")
 * @param collapseTitle 스크롤과 무관하게 제목을 접어야 할 때 true (예: 검색창 포커스 중)
 * @param titleTrailing 제목 우측 보조 텍스트 (예: 즐겨찾기 개수)
 */
@Composable
fun TabScreenScaffold(
    title: String,
    listState: LazyListState,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    collapseTitle: Boolean = false,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentScrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val headerShadow by animateDpAsState(
        targetValue = if (contentScrolled) 4.dp else 0.dp,
        label = "headerShadow",
    )

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = headerShadow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = !contentScrolled && !collapseTitle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        titleTrailing?.invoke(this)
                    }
                }
                header()
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (snackbarHostState != null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
