package com.kimdev.petmap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.kimdev.petmap.R

/**
 * 즐겨찾기 하트 토글 버튼 (카드/상세 공용).
 * 켜는 순간 하트가 통통 튀는 스프링 팝 + 색 전환 애니메이션.
 */
@Composable
fun FavoriteIconButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    uncheckedTint: Color = MaterialTheme.colorScheme.outline,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        AnimatedFavoriteIcon(isFavorite = isFavorite, uncheckedTint = uncheckedTint)
    }
}

/**
 * 하트 아이콘만 (팝 애니메이션 포함). IconButton 이 아닌 다른 컨테이너
 * (미리보기 시트의 OutlinedIconButton 등)에 넣을 때 사용한다.
 */
@Composable
fun AnimatedFavoriteIcon(
    isFavorite: Boolean,
    uncheckedTint: Color = MaterialTheme.colorScheme.outline,
) {
    val scale = remember { Animatable(1f) }
    // 최초 컴포지션(화면 진입·스크롤 재사용)에서는 튀지 않도록 이전 값과 비교한다
    var previous by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (previous != isFavorite) {
            previous = isFavorite
            if (isFavorite) {
                scale.snapTo(0.55f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }
    val tint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else uncheckedTint,
        label = "favoriteTint",
    )
    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
        tint = tint,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
