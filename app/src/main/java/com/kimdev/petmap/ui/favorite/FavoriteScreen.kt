package com.kimdev.petmap.ui.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.ui.components.BannerAd
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.components.PlaceCard

@Composable
fun FavoriteScreen(
    onPlaceClick: (String) -> Unit,
    onExplore: () -> Unit = {},
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
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
            if (favorites.isNotEmpty()) {
                Text(
                    "${favorites.size}곳",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (favorites.isEmpty()) {
                EmptyState(
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
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites, key = { it.id }) { place ->
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
