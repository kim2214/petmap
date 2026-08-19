package com.kimdev.petmap.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.formatDistance
import com.kimdev.petmap.ui.common.rememberIsOpenNow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 누르는 동안 살짝 줄어드는 촉감 (리플과 함께 눌림을 시각화)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "cardPressScale",
    )
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(place)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    // 한국 상호는 "○○동물병원 ○○점"처럼 구분 정보가 뒤에 와서 1줄 말줄임이 치명적
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    place.roadAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // FlowRow: 큰 글꼴 배율이나 긴 카테고리 라벨에서 뒤쪽 요소(영업중 뱃지)가
                // 화면 밖으로 잘리는 대신 다음 줄로 흐르게 한다.
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
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
                PetBadges(place)
                // 즐겨찾기 메모 (즐겨찾기 화면에서만 값이 채워짐)
                place.memo?.let { memo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            memo,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            FavoriteIconButton(
                isFavorite = place.isFavorite,
                onClick = onToggleFavorite,
            )
        }
    }
}

@Composable
fun CategoryAvatar(place: Place) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(place.category.softColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = place.category.icon,
            contentDescription = null,
            tint = place.category.color,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun CategoryTag(place: Place) {
    Surface(
        color = place.category.softColor,
        contentColor = place.category.color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            stringResource(place.category.labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 반려동물 동반 정보 뱃지 (소형견 가능 / 실내·실외 가능 등) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetBadges(place: Place) {
    val paw = place.petInfo.allowedPetSize?.let { stringResource(R.string.pet_size_format, it) }
    val indoorLabel = stringResource(R.string.pet_indoor_allowed)
    val outdoorLabel = stringResource(R.string.pet_outdoor_allowed)
    val badges = buildList {
        paw?.let { add(it) }
        if (place.petInfo.indoorAllowed) add(indoorLabel)
        if (place.petInfo.outdoorAllowed) add(outdoorLabel)
    }.take(3)
    if (badges.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        badges.forEach { text ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
fun OpenBadge(place: Place) {
    val isOpen = rememberIsOpenNow(place.operatingTime, place.closedDays)
    when (isOpen) {
        true -> Text(
            stringResource(R.string.label_open_now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        false -> Text(
            stringResource(R.string.label_closed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        null -> Unit
    }
}
