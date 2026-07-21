package com.kimdev.petmap.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.ui.components.BannerAd
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.components.PlaceCard

@Composable
fun ListScreen(
    onPlaceClick: (String) -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "목록",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("장소·주소 검색") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "지우기")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.recordRecentSearch(state.query)
                focusManager.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .onFocusChanged { searchFocused = it.isFocused },
        )
        CategoryFilterRow(
            selected = state.selectedCategories,
            onToggle = viewModel::toggleCategory,
            onClearAll = viewModel::clearCategories,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 정렬/필터 토글
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.sortByDistance,
                onClick = { viewModel.setSortByDistance(!state.sortByDistance) },
                enabled = state.hasLocation,
                label = { Text(if (state.hasLocation) "거리순" else "거리순(위치 필요)") },
                leadingIcon = leadingCheck(state.sortByDistance),
            )
            FilterChip(
                selected = state.openNowOnly,
                onClick = { viewModel.setOpenNowOnly(!state.openNowOnly) },
                label = { Text("영업중") },
                leadingIcon = leadingCheck(state.openNowOnly),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // 최근 검색어 (검색창 포커스 + 입력 비어있음)
                searchFocused && state.query.isBlank() && state.recentSearches.isNotEmpty() ->
                    RecentSearchList(
                        recents = state.recentSearches,
                        onPick = { viewModel.onQueryChange(it) },
                        onRemove = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearRecentSearches() },
                    )

                state.isLoading && state.places.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                // 조회 실패 → "결과 없음"과 구분해 에러 상태 + 재시도 동선 제공
                state.isError && state.places.isEmpty() -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "장소를 불러오지 못했어요",
                    description = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    action = { Button(onClick = { viewModel.retry() }) { Text("다시 시도") } },
                )

                state.places.isEmpty() -> {
                    val (title, desc) = when {
                        state.openNowOnly -> "지금 영업중인 장소가 없어요" to "영업중 필터를 끄거나 다른 지역에서 찾아보세요."
                        state.query.isNotBlank() -> "'${state.query}' 검색 결과가 없어요" to "다른 키워드로 찾아보세요."
                        state.selectedCategories.isNotEmpty() -> "조건에 맞는 장소가 없어요" to "필터를 줄이면 더 많은 장소가 보여요."
                        else -> "표시할 장소가 없어요" to null
                    }
                    // 0건일 때 곧바로 빠져나갈 수 있는 복구 동선
                    val recovery: (@Composable () -> Unit)? = when {
                        state.openNowOnly -> {
                            { Button(onClick = { viewModel.setOpenNowOnly(false) }) { Text("영업중 필터 끄기") } }
                        }
                        state.query.isNotBlank() -> {
                            { Button(onClick = { viewModel.onQueryChange("") }) { Text("검색 지우기") } }
                        }
                        state.selectedCategories.isNotEmpty() -> {
                            { Button(onClick = { viewModel.clearCategories() }) { Text("필터 초기화") } }
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

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.places, key = { it.id }) { place ->
                        PlaceCard(
                            place = place,
                            onClick = { onPlaceClick(place.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(place) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        BannerAd()
    }
}

@Composable
private fun RecentSearchList(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "최근 검색",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearAll) { Text("전체 삭제") }
            }
        }
        items(recents, key = { it }) { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(term) }
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    term,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemove(term) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun leadingCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
        null
    }
