package com.kimdev.petmap.ui.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.markerColor
import com.naver.maps.map.overlay.OverlayImage
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 단일 장소 마커의 핀 팁(뾰족한 아래 끝)이 이미지에서 차지하는 세로 비율.
 * [renderMarkerBitmap]의 치수와 반드시 일치해야 마커가 정확한 좌표를 가리킨다.
 */
val MarkerAnchor = Offset(0.5f, 0.963f)

/**
 * 카테고리별 마커 아이콘을 미리 생성해 캐싱한다.
 * 컬러 물방울 핀 + 흰색 외곽선 + 가운데 흰색 카테고리 아이콘 형태.
 * 카테고리 수가 고정이라 최초 1회만 비트맵을 굽고 재사용한다.
 */
@Composable
fun rememberCategoryMarkers(): Map<PlaceCategory, OverlayImage> {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val painters = PlaceCategory.values().associateWith { rememberVectorPainter(it.icon) }
    return remember(density, layoutDirection, painters) {
        PlaceCategory.values().associateWith { category ->
            OverlayImage.fromBitmap(
                renderMarkerBitmap(category.markerColor, painters.getValue(category), density, layoutDirection),
            )
        }
    }
}

private fun renderMarkerBitmap(
    color: Color,
    painter: VectorPainter,
    density: Density,
    layoutDirection: LayoutDirection,
): Bitmap {
    val d = density.density
    fun dp(v: Float) = v * d

    val halo = dp(1.8f)          // 흰색 외곽선 두께
    val headR = dp(15f)          // 핀 머리(원) 반지름
    val tailLen = dp(15f)        // 머리 아래에서 팁까지 길이
    val w = (headR + halo) * 2f
    val headCx = w / 2f
    val headCy = headR + halo
    val tipY = headCy + headR + tailLen
    val h = tipY + halo

    val bitmap = ImageBitmap(ceil(w).toInt(), ceil(h).toInt())
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(density, layoutDirection, canvas, Size(w, h)) {
        // 흰색 외곽선(살짝 큰 핀)
        drawPin(Color.White, headCx, headCy, headR + halo, tipY + halo)
        // 카테고리 색 핀
        drawPin(color, headCx, headCy, headR, tipY)
        // 가운데 흰색 아이콘
        val iconSize = headR * 1.15f
        translate(headCx - iconSize / 2f, headCy - iconSize / 2f) {
            with(painter) {
                draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
    return bitmap.asAndroidBitmap()
}

/** 원형 머리 + 아래로 뾰족한 꼬리로 이뤄진 물방울 핀을 그린다. */
private fun DrawScope.drawPin(color: Color, cx: Float, cy: Float, r: Float, tipY: Float) {
    drawCircle(color, radius = r, center = Offset(cx, cy))
    // 팁에서 원에 접하는 두 점을 밑변으로 하는 삼각형(원과 매끄럽게 이어짐)
    val dist = tipY - cy
    val ang = acos((r / dist).coerceIn(-1f, 1f))
    val sx = sin(ang) * r
    val cyOff = cos(ang) * r
    val tail = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + sx, cy + cyOff)
        lineTo(cx - sx, cy + cyOff)
        close()
    }
    drawPath(tail, color)
}
