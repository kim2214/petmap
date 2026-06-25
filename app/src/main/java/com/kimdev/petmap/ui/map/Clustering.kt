package com.kimdev.petmap.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kimdev.petmap.domain.model.Place
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** 지도에 그릴 항목: 단일 장소(single != null) 또는 여러 장소가 묶인 클러스터 */
data class MapCluster(
    val id: String,
    val lat: Double,
    val lng: Double,
    val members: List<Place>,
) {
    val count: Int get() = members.size
    val single: Place? get() = if (members.size == 1) members[0] else null

    /** 멤버들이 차지하는 대략적 범위(m). 0에 가까우면 사실상 같은 좌표 → 줌해도 안 쪼개짐 */
    fun spanMeters(): Double {
        if (members.size < 2) return 0.0
        val avgLat = members.sumOf { it.lat } / members.size
        val dLat = (members.maxOf { it.lat } - members.minOf { it.lat }) * 111_320.0
        val dLng = (members.maxOf { it.lng } - members.minOf { it.lng }) *
            111_320.0 * max(0.01, cos(Math.toRadians(avgLat)))
        return max(dLat, dLng)
    }
}

/** 줌 레벨에 따른 장소 조회 반경(km) 근사 */
fun radiusForZoom(zoom: Double): Double = when {
    zoom >= 15.0 -> 1.5
    zoom >= 13.0 -> 4.0
    zoom >= 11.0 -> 12.0
    zoom >= 9.0 -> 40.0
    else -> 120.0
}

/**
 * 화면상 약 [cellPx]px 크기의 격자로 장소를 묶는 그리드 클러스터링.
 * 줌이 높을수록 셀의 실제(위경도) 크기가 작아져 더 잘게 나뉜다.
 */
fun clusterPlaces(places: List<Place>, zoom: Double, cellPx: Double = 72.0): List<MapCluster> {
    if (places.isEmpty()) return emptyList()
    val avgLat = places.sumOf { it.lat } / places.size
    val cosLat = max(0.01, cos(Math.toRadians(avgLat)))
    // 웹 메르카토르 기준 미터/픽셀
    val metersPerPx = 156543.03392 * cosLat / 2.0.pow(zoom)
    val cellMeters = max(1.0, cellPx * metersPerPx)
    val latStep = cellMeters / 111_320.0
    val lngStep = cellMeters / (111_320.0 * cosLat)

    val groups = LinkedHashMap<String, MutableList<Place>>()
    for (p in places) {
        val gx = floor(p.lat / latStep).toLong()
        val gy = floor(p.lng / lngStep).toLong()
        groups.getOrPut("$gx:$gy") { mutableListOf() }.add(p)
    }

    return groups.values.map { members ->
        if (members.size == 1) {
            val p = members[0]
            MapCluster(id = p.id, lat = p.lat, lng = p.lng, members = members)
        } else {
            val cLat = members.sumOf { it.lat } / members.size
            val cLng = members.sumOf { it.lng } / members.size
            MapCluster(
                id = "c_${members.first().id}_${members.size}",
                lat = cLat, lng = cLng, members = members,
            )
        }
    }
}

/** 개수가 그려진 원형 클러스터 마커 비트맵 생성 */
fun makeClusterBitmap(count: Int): Bitmap {
    val sizePx = when {
        count >= 100 -> 140
        count >= 10 -> 120
        else -> 100
    }
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f

    // 반투명 외곽 링 (브랜드 그린)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553E7D44.toInt() }
    canvas.drawCircle(r, r, r, ring)
    // 채워진 원
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E7D44.toInt() }
    canvas.drawCircle(r, r, r * 0.74f, fill)
    // 개수 텍스트
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.32f
        isFakeBoldText = true
    }
    val label = if (count >= 1000) "999+" else count.toString()
    val ty = r - (tp.descent() + tp.ascent()) / 2f
    canvas.drawText(label, r, ty, tp)
    return bmp
}
