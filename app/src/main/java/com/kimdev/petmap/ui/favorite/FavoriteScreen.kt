package com.kimdev.petmap.ui.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.domain.model.Place
import kotlinx.coroutines.launch
import com.kimdev.petmap.ui.components.BannerAd
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.components.PlaceCard

@Composable
fun FavoriteScreen(
    onPlaceClick: (String) -> Unit,
    onExplore: () -> Unit = {},
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 하트 탭으로 목록에서 즉시 사라지므로, 실수 방지용 실행취소 동선을 준다.
    fun removeWithUndo(place: Place) {
        viewModel.toggleFavorite(place)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "'${place.name}' 즐겨찾기에서 제거했어요",
                actionLabel = "실행취소",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.toggleFavorite(place)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "즐겨찾기",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.favorites.isNotEmpty()) {
                Text(
                    "${state.favorites.size}곳",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 즐겨찾기가 쌓이면 카테고리로 좁혀 볼 수 있게 한다 (하나도 없을 땐 숨김)
        if (state.totalCount > 0) {
            CategoryFilterRow(
                selected = state.selectedCategories,
                onToggle = viewModel::toggleCategory,
                onClearAll = viewModel::clearCategories,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // 첫 로드 중엔 스피너 (빈 상태 문구가 잘못 깜빡이는 것 방지)
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = "아직 즐겨찾기한 장소가 없어요",
                    description = "마음에 드는 곳의 ♡ 를 눌러 저장하면\n여기 모아서 볼 수 있어요.",
                    action = {
                        FilledTonalButton(onClick = onExplore) {
                            Icon(Icons.Filled.Explore, contentDescription = null)
                            Text("장소 둘러보기", modifier = Modifier.padding(start = 6.dp))
                        }
                    },
                )

                state.favorites.isEmpty() -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "조건에 맞는 즐겨찾기가 없어요",
                    description = "다른 카테고리를 선택하거나 필터를 초기화해 보세요.",
                    action = {
                        Button(onClick = viewModel::clearCategories) { Text("필터 초기화") }
                    },
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        BannerAd()
    }
}
