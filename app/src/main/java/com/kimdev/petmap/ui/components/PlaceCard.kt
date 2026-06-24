package com.kimdev.petmap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.OpeningHours
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    place.roadAddress,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(place.category.label) },
                    )
                    place.distanceMeters?.let { Text(formatDistance(it), style = MaterialTheme.typography.labelMedium) }
                    OpenBadge(place)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (place.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "즐겨찾기",
                )
            }
        }
    }
}

@Composable
private fun OpenBadge(place: Place) {
    val open = OpeningHours.isOpenNow(place.operatingTime, place.closedDays, LocalDateTime.now())
    when (open) {
        true -> Text(
            "영업중",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        false -> Text(
            "영업종료",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        null -> Unit
    }
}

private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()}m" else "${(m / 100).roundToInt() / 10.0}km"
