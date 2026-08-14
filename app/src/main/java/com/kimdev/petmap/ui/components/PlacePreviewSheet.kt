package com.kimdev.petmap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.formatDistance

/**
 * 지도 마커 탭 시 뜨는 미리보기 바텀시트 내용.
 * 목록 카드와 같은 시각 언어(컬러 아바타·카테고리 태그·거리·영업중)로 통일.
 */
@Composable
fun PlacePreviewSheet(
    place: Place,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDirections: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(place)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    CategoryTag(place)
                    place.distanceMeters?.let {
                        Text(
                            formatDistance(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OpenBadge(place)
                }
            }
        }

        Text(
            text = place.roadAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )

        place.operatingTime?.let {
            Text(
                text = stringResource(R.string.operating_time_format, it),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 반려동물 동반 요약
        val indoorLabel = stringResource(R.string.pet_indoor_allowed)
        val outdoorLabel = stringResource(R.string.pet_outdoor_allowed)
        val petSummary = buildList {
            place.petInfo.allowedPetSize?.let { add(it) }
            if (place.petInfo.indoorAllowed) add(indoorLabel)
            if (place.petInfo.outdoorAllowed) add(outdoorLabel)
        }.joinToString(" · ")
        if (petSummary.isNotEmpty()) {
            Text(
                text = stringResource(R.string.pet_size_format, petSummary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(onClick = onToggleFavorite) {
                // 카드/상세와 동일한 팝 애니메이션 하트 (스크린리더 상태 전달 포함)
                AnimatedFavoriteIcon(
                    isFavorite = isFavorite,
                    uncheckedTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDirections, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Directions, contentDescription = null)
                Text(stringResource(R.string.action_directions), modifier = Modifier.padding(start = 6.dp))
            }
            Button(onClick = onDetail, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_detail))
            }
        }
    }
}
