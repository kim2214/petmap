package com.kimdev.petmap.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * 목록 로딩 중 보여주는 PlaceCard 모양 스켈레톤.
 * 가운데 스피너 대신 실제 카드 레이아웃을 흉내 내 로딩 후 화면 점프를 줄인다.
 */
@Composable
fun PlaceCardSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            // TalkBack 이 빈 카드들을 항목처럼 읽지 않게 시맨틱을 비운다
            .clearAndSetSemantics { },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

/** 로딩 상태 전체를 채우는 스켈레톤 목록. */
@Composable
fun PlaceListSkeleton(modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier = modifier) {
        repeat(count) { PlaceCardSkeleton() }
    }
}

/** 좌→우로 흐르는 shimmer 브러시. */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // 화면 폭(px)과 무관하게 충분히 큰 고정 범위를 왕복 없이 반복 이동
    val x by transition.animateFloat(
        initialValue = -SHIMMER_BAND,
        targetValue = SHIMMER_BAND * 2,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = base.copy(alpha = 0.45f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + SHIMMER_BAND, SHIMMER_BAND / 3f),
    )
}

private const val SHIMMER_BAND = 1200f
