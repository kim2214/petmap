package com.kimdev.petmap.ui.list

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kimdev.petmap.core.util.openAppSettings
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.components.LocationSettingsDialog
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.PlaceCardSkeleton
import com.kimdev.petmap.ui.components.PlaceListSkeleton
import com.kimdev.petmap.ui.components.RecentSearchList
import com.kimdev.petmap.ui.components.SearchTextField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ListScreen(
    onPlaceClick: (String) -> Unit,
    reselects: Flow<Unit> = emptyFlow(),
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val resources = LocalResources.current
    var searchFocused by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // when 분기 안에서 만들면 최근 검색어 오버레이로 갈아탈 때 컴포지션에서 제거되어
    // 스크롤 위치가 사라진다 → 분기 밖에서 생성해 유지한다.
    val listState = rememberLazyListState()
    // 검색 조건이 바뀌면 새 결과를 처음부터 보여준다. drop(1): 최초 구성·회전 복원 시에는
    // 조건이 "바뀐" 게 아니므로 보던 위치를 유지한다.
    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(state.query, state.selectedCategories, state.sortByDistance, state.openNowOnly)
        }
            .distinctUntilChanged()
            .drop(1)
            .collect { listState.scrollToItem(0) }
    }
    // 하단 탭 "목록" 재선택 → 맨 위로
    LaunchedEffect(Unit) {
        reselects.collect { listState.animateScrollToItem(0) }
    }

    // 검색 오버레이/검색어가 있으면 뒤로가기로 화면을 나가는 대신 검색을 닫는다(지도 화면과 동일)
    BackHandler(enabled = searchFocused || state.query.isNotBlank()) {
        viewModel.onQueryChange("")
        focusManager.clearFocus()
    }

    // rememberSaveable: 화면 재구성(탭 전환·회전) 후 거부한 권한을 다시 묻지 않는다.
    var requestedLocationOnce by rememberSaveable { mutableStateOf(false) }
    var pendingDistanceSort by rememberSaveable { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }

    // "거리순" 칩에서 위치 권한을 바로 요청할 수 있게 한다 (지도 탭을 안 거쳐도 됨)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ) { results ->
        // 거부됐다면 대기 중인 거리순 요청을 지운다. 남겨두면 사용자가 나중에 시스템 설정에서
        // 권한을 켜고 돌아왔을 때 의도하지 않은 시점에 거리순이 켜진다.
        if (results.values.none { it }) pendingDistanceSort = false
    }
    val granted = locationPermissions.allPermissionsGranted

    // 칩 탭으로 권한을 요청했고 사용자가 허용함 → 위치 확보 후 거리순 켜기
    LaunchedEffect(granted) {
        if (granted && pendingDistanceSort) {
            pendingDistanceSort = false
            viewModel.enableDistanceSortWithLocation()
        }
    }

    // 일회성 이벤트(권한은 있는데 위치 확보 실패 등)는 상태가 아니라 이벤트로 받는다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ListEvent.LocationUnavailable ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.list_location_unavailable))
            }
        }
    }

    // 스크롤이 시작되면 큰 제목을 접어 결과 영역을 넓히고, 헤더에 구분 그림자를 띄운다.
    // 검색 중(최근 검색 오버레이)에도 제목을 접어 입력에 집중시킨다.
    val contentScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val headerShadow by animateDpAsState(
        targetValue = if (contentScrolled) 4.dp else 0.dp,
        label = "headerShadow",
    )

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = headerShadow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = !contentScrolled && !searchFocused,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        stringResource(R.string.nav_list),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(start = 20.dp, top = 24.dp, bottom = 4.dp)
                            .semantics { heading() },
                    )
                }
                SearchTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    onClear = { viewModel.onQueryChange("") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.recordRecentSearch(state.query)
                        focusManager.clearFocus()
                    }),
                    onFocusChanged = { searchFocused = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                CategoryFilterRow(
                    selected = state.selectedCategories,
                    onToggle = viewModel::toggleCategory,
                    onClearAll = viewModel::clearCategories,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // 정렬/필터 토글 + 결과 개수
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.sortByDistance,
                        onClick = {
                            when {
                                state.hasLocation -> viewModel.setSortByDistance(!state.sortByDistance)
                                // 권한은 있는데 위치만 없음 → 위치 재확보 시도
                                granted -> viewModel.enableDistanceSortWithLocation()
                                locationPermissions.shouldShowRationale || !requestedLocationOnce -> {
                                    requestedLocationOnce = true
                                    pendingDistanceSort = true
                                    locationPermissions.launchMultiplePermissionRequest()
                                }
                                else -> showLocationSettingsDialog = true // 영구 거부 → 설정 안내
                            }
                        },
                        label = { Text(stringResource(R.string.filter_distance)) },
                        leadingIcon = leadingCheck(state.sortByDistance),
                    )
                    FilterChip(
                        selected = state.openNowOnly,
                        onClick = { viewModel.setOpenNowOnly(!state.openNowOnly) },
                        label = { Text(stringResource(R.string.label_open_now)) },
                        leadingIcon = leadingCheck(state.openNowOnly),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 검색어·필터를 좁힌 효과를 가늠할 수 있게 결과 개수를 보여준다
                    if (!state.isLoading && state.places.isNotEmpty()) {
                        val countRes =
                            if (state.canLoadMore || state.reachedLimit) R.string.list_count_more_format
                            else R.string.list_count_format
                        Text(
                            stringResource(countRes, state.places.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // 최근 검색어 (검색창 포커스 + 입력 비어있음)
                searchFocused && state.query.isBlank() && state.recentSearches.isNotEmpty() ->
                    RecentSearchList(
                        recents = state.recentSearches,
                        onPick = {
                            viewModel.onQueryChange(it)
                            // 재선택도 최근 검색 최상단으로 승격 + 키보드를 내려 결과가 보이게 한다
                            viewModel.recordRecentSearch(it)
                            focusManager.clearFocus()
                        },
                        onRemove = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearRecentSearches() },
                        modifier = Modifier.fillMaxSize(),
                    )

                state.isLoading && state.places.isEmpty() ->
                    PlaceListSkeleton(modifier = Modifier.fillMaxSize())

                // 조회 실패 → "결과 없음"과 구분해 에러 상태 + 재시도 동선 제공
                state.isError && state.places.isEmpty() -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.empty_error_title),
                    description = stringResource(R.string.empty_error_desc),
                    action = { Button(onClick = { viewModel.retry() }) { Text(stringResource(R.string.action_retry)) } },
                )

                state.places.isEmpty() -> {
                    val (title, desc) = when {
                        state.openNowOnly -> stringResource(R.string.empty_open_now_title) to stringResource(R.string.empty_open_now_desc)
                        state.query.isNotBlank() -> stringResource(R.string.empty_search_title_format, state.query) to stringResource(R.string.empty_search_desc)
                        state.selectedCategories.isNotEmpty() -> stringResource(R.string.empty_filter_title) to stringResource(R.string.empty_filter_desc)
                        else -> stringResource(R.string.empty_none_title) to null
                    }
                    // 0건일 때 곧바로 빠져나갈 수 있는 복구 동선
                    val recovery: (@Composable () -> Unit)? = when {
                        state.openNowOnly -> {
                            { Button(onClick = { viewModel.setOpenNowOnly(false) }) { Text(stringResource(R.string.action_open_now_off)) } }
                        }
                        state.query.isNotBlank() -> {
                            { Button(onClick = { viewModel.onQueryChange("") }) { Text(stringResource(R.string.action_clear_search)) } }
                        }
                        state.selectedCategories.isNotEmpty() -> {
                            { Button(onClick = { viewModel.clearCategories() }) { Text(stringResource(R.string.action_reset_filter)) } }
                        }
                        else -> null
                    }
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = title,
                        description = desc,
                        action = recovery,
                    )
                }

                else -> {
                    // 마지막에서 몇 칸 앞에 도달하면 다음 페이지를 미리 불러온다(스크롤이 끊기지 않게).
                    val shouldLoadMore by remember(listState, state.places.size) {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= state.places.size - LOAD_MORE_THRESHOLD
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMore()
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.places, key = { it.id }) { place ->
                            PlaceCard(
                                place = place,
                                onClick = {
                                    // 가장 흔한 동선(타이핑 → 결과 카드 탭)에서도 검색어를 기록한다
                                    if (state.query.isNotBlank()) viewModel.recordRecentSearch(state.query)
                                    onPlaceClick(place.id)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(place) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading_more") { PlaceCardSkeleton() }
                        } else if (state.reachedLimit) {
                            // 상한까지 불러왔음을 숨기지 않고 좁히는 방법을 안내한다
                            item(key = "reached_limit") {
                                Text(
                                    stringResource(R.string.list_reached_limit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

    }

    if (showLocationSettingsDialog) {
        LocationSettingsDialog(
            message = stringResource(R.string.location_message_list),
            onOpenSettings = { context.openAppSettings() },
            onDismiss = { showLocationSettingsDialog = false },
        )
    }
}

private fun leadingCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
        null
    }

/** 목록 끝에서 이만큼 앞에 도달하면 다음 페이지를 미리 불러온다. */
private const val LOAD_MORE_THRESHOLD = 5
