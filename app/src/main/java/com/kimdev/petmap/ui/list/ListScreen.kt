package com.kimdev.petmap.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("장소·주소 검색") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        CategoryFilterRow(
            selected = state.selectedCategory,
            onSelect = viewModel::selectCategory,
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
            state.isLoading && state.places.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.places.isEmpty() -> {
                val (title, desc) = when {
                    state.openNowOnly -> "지금 영업중인 장소가 없어요" to "영업중 필터를 끄거나 다른 지역에서 찾아보세요."
                    state.query.isNotBlank() -> "'${state.query}' 검색 결과가 없어요" to "다른 키워드로 찾아보세요."
                    else -> "표시할 장소가 없어요" to null
                }
                EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = title,
                    description = desc,
                )
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.places, key = { it.id }) { place ->
                    PlaceCard(
                        place = place,
                        onClick = { onPlaceClick(place.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(place) },
                    )
                }
            }
        }
        }

        BannerAd()
    }
}

private fun leadingCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
        null
    }
