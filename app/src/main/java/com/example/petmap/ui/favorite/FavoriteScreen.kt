package com.example.petmap.ui.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.petmap.ui.components.PlaceCard

@Composable
fun FavoriteScreen(
    onPlaceClick: (String) -> Unit,
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("즐겨찾기한 장소가 없습니다")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(favorites, key = { it.id }) { place ->
                PlaceCard(
                    place = place,
                    onClick = { onPlaceClick(place.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(place) },
                )
            }
        }
    }
}
