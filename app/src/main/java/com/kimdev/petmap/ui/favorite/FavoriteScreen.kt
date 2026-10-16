package com.kimdev.petmap.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.R
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.components.TabScreenScaffold
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.PlaceListSkeleton
import com.kimdev.petmap.ui.components.SearchTextField

@Composable
fun FavoriteScreen(
    onPlaceClick: (String) -> Unit,
    onExplore: () -> Unit = {},
    reselects: Flow<Unit> = emptyFlow(),
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recent by viewModel.recentState.collectAsStateWithLifecycle()
    // 즐겨찾기 / 최근 본 전환. 두 목록은 스크롤 위치를 따로 기억한다
    var showRecent by rememberSaveable { mutableStateOf(false) }
    var confirmClearRecent by rememberSaveable { mutableStateOf(false) }
    val recentListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 하단 탭 "즐겨찾기" 재선택 → 맨 위로
    LaunchedEffect(Unit) {
        reselects.collect { (if (showRecent) recentListState else listState).animateScrollToItem(0) }
    }
    // 설정 변경(언어 등)에 따라 갱신되는 리소스 접근 (Context 경유는 lint 에러)
    val resources = LocalResources.current

    // 하트 탭으로 목록에서 즉시 사라지므로, 실수 방지용 실행취소 동선을 준다.
    // 토글이 아니라 삭제/스냅샷 복원을 쓴다: 토글로 되살리면 메모가 비고 목록 맨 위로 올라가며,
    // 퇴장 애니메이션 중 하트를 다시 누르면 삭제가 아니라 재추가가 되어 "실행취소"가 거꾸로 동작했다.
    fun removeWithUndo(place: Place) {
        scope.launch {
            // 이미 지워진 카드(퇴장 애니메이션 중 재탭)면 아무것도 하지 않는다
            val snapshot = viewModel.removeFavorite(place.id) ?: return@launch
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.favorite_removed_format, place.name),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreFavorite(snapshot)
        }
    }

    // 최근 본 장소 지우기 — 밀어서 지우므로 실수 대비 실행취소를 준다(원래 자리로 복원)
    fun removeRecentWithUndo(place: Place) {
        val index = viewModel.removeRecent(place.id) ?: return
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.recent_removed_format, place.name),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreRecent(place.id, index)
        }
    }

    TabScreenScaffold(
        title = stringResource(R.string.nav_favorite),
        listState = if (showRecent) recentListState else listState,
        snackbarHostState = snackbarHostState,
        titleTrailing = {
        if (showRecent) {
            if (recent.places.isNotEmpty()) {
                TextButton(onClick = { confirmClearRecent = true }) { Text(stringResource(R.string.action_clear)) }
            }
        } else if (state.favorites.isNotEmpty()) {
            Text(
                stringResource(R.string.favorite_count_format, state.favorites.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        },
        header = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                listOf(false to R.string.saved_tab_favorite, true to R.string.saved_tab_recent)
                    .forEachIndexed { index, (recentTab, label) ->
                        SegmentedButton(
                            selected = showRecent == recentTab,
                            onClick = { showRecent = recentTab },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) { Text(stringResource(label)) }
                    }
            }
            // 즐겨찾기가 쌓이면 검색·카테고리로 좁혀 볼 수 있게 한다 (하나도 없을 땐 숨김)
            if (!showRecent && state.totalCount > 0) {
                SearchTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    onClear = { viewModel.onQueryChange("") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                CategoryFilterRow(
                    selected = state.selectedCategories,
                    onToggle = viewModel::toggleCategory,
                    onClearAll = viewModel::clearCategories,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        if (showRecent) {
            RecentContent(
                state = recent,
                listState = recentListState,
                onPlaceClick = onPlaceClick,
                onToggleFavorite = viewModel::toggleFavorite,
                onRemove = ::removeRecentWithUndo,
                onExplore = onExplore,
            )
            return@TabScreenScaffold
        }
        when {
            // 첫 로드 중엔 스켈레톤 (빈 상태 문구가 잘못 깜빡이는 것 방지)
            state.isLoading -> PlaceListSkeleton(modifier = Modifier.fillMaxSize(), count = 4)

            state.totalCount == 0 -> EmptyState(
                icon = Icons.Filled.FavoriteBorder,
                title = stringResource(R.string.favorite_empty_title),
                description = stringResource(R.string.favorite_empty_desc),
                action = {
                    FilledTonalButton(onClick = onExplore) {
                        Icon(Icons.Filled.Explore, contentDescription = null)
                        Text(stringResource(R.string.favorite_explore), modifier = Modifier.padding(start = 6.dp))
                    }
                },
            )

            state.favorites.isEmpty() -> EmptyState(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.favorite_filter_empty_title),
                description = stringResource(R.string.favorite_filter_empty_desc),
                action = {
                    // 0건의 원인(검색어/카테고리)에 맞는 복구 버튼
                    if (state.query.isNotBlank()) {
                        Button(onClick = { viewModel.onQueryChange("") }) { Text(stringResource(R.string.action_clear_search)) }
                    } else {
                        Button(onClick = viewModel::clearCategories) { Text(stringResource(R.string.action_reset_filter)) }
                    }
                },
            )

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.favorites, key = { it.id }) { place ->
                    PlaceCard(
                        place = place,
                        onClick = { onPlaceClick(place.id) },
                        onToggleFavorite = { removeWithUndo(place) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmClearRecent) {
        AlertDialog(
            onDismissRequest = { confirmClearRecent = false },
            title = { Text(stringResource(R.string.recent_clear_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRecent()
                    confirmClearRecent = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRecent = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** "최근 본" 목록: 최신 우선, 하트는 즐겨찾기 토글(카드는 그대로 남는다) */
@Composable
private fun RecentContent(
    state: RecentUiState,
    listState: LazyListState,
    onPlaceClick: (String) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onExplore: () -> Unit,
) {
    when {
        state.isLoading -> PlaceListSkeleton(modifier = Modifier.fillMaxSize(), count = 4)

        state.places.isEmpty() -> EmptyState(
            icon = Icons.Filled.History,
            title = stringResource(R.string.recent_empty_title),
            description = stringResource(R.string.recent_empty_desc, RecentPlaceStore.MAX),
            action = {
                FilledTonalButton(onClick = onExplore) {
                    Icon(Icons.Filled.Explore, contentDescription = null)
                    Text(stringResource(R.string.favorite_explore), modifier = Modifier.padding(start = 6.dp))
                }
            },
        )

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "swipe_hint") {
                Text(
                    text = stringResource(R.string.recent_swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(state.places, key = { it.id }) { place ->
                SwipeToRemove(onRemove = { onRemove(place) }, modifier = Modifier.animateItem()) {
                    PlaceCard(
                        place = place,
                        onClick = { onPlaceClick(place.id) },
                        onToggleFavorite = { onToggleFavorite(place) },
                    )
                }
            }
        }
    }
}

/**
 * 양방향으로 밀어 지우는 행. 밀기는 보조 동작이라 스크린리더용 동작("최근 본 목록에서 지우기")도 단다.
 * 지운 행은 목록에서 빠지며 컴포지션을 떠나므로 상태를 되돌릴 필요가 없고, 실행취소로 돌아오면 새 상태로 그려진다.
 */
@Composable
private fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val removeLabel = stringResource(R.string.recent_remove_action)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
        },
        onDismiss = { onRemove() },
        backgroundContent = {
            val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                contentAlignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}
