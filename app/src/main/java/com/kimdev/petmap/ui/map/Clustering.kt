package com.kimdev.petmap.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.distanceMeters
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
    /**
     * 저줌 SQL 그리드 집계 클러스터의 실제 개수. 이 경우 개별 좌표를 모르므로 [members] 는 비어 있다.
     * null 이면 [members] 크기를 개수로 사용한다.
     */
    val aggregatedCount: Int? = null,
    /** 집계 클러스터의 원본 셀 — 탭 시 셀 범위 재조회(목록 펼치기)에 쓴다 */
    val cell: GeoClusterCell? = null,
) {
    val count: Int get() = aggregatedCount ?: members.size
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

/**
 * 마지막으로 데이터를 불러온 지점/줌과 현재 카메라를 비교해
 * "이 지역에서 다시 검색"이 필요한지 판단.
 * - 조회 반경의 [movedFraction] 이상 이동했거나
 * - 줌 단계가 바뀌어 조회 반경 자체가 달라지면 true.
 */
fun isResearchNeeded(
    loadedLat: Double,
    loadedLng: Double,
    loadedZoom: Double,
    currentLat: Double,
    currentLng: Double,
    currentZoom: Double,
    movedFraction: Double = 0.45,
): Boolean {
    val moved = distanceMeters(loadedLat, loadedLng, currentLat, currentLng)
    val radiusChanged = radiusForZoom(currentZoom) != radiusForZoom(loadedZoom)
    return moved > radiusForZoom(loadedZoom) * 1000.0 * movedFraction || radiusChanged
}

/**
 * 클러스터 버블에 표시할 개수 라벨. 네 자리 이상은 "1.2천" 형태로 줄여 원 안에 들어가게 한다.
 * (저줌 집계 클러스터는 개수가 수천까지 나온다)
 */
fun clusterLabel(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 10_000 -> "${count / 1000}.${(count % 1000) / 100}천"
    else -> "${count / 1000}천"
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

/**
 * 개수가 그려진 원형 클러스터 마커 비트맵 생성.
 *
 * [density] 는 화면 배율(dp→px). 지정하지 않으면 3.0 기준으로 굽는다.
 * Marker 의 크기가 SizeAuto 라 비트맵의 실제 px 가 화면 크기를 결정하므로, 배율을 반영하지 않으면
 * 저배율 기기에서 버블이 과도하게 커져 인접 클러스터끼리 겹친다(카테고리 핀은 이미 dp 기준).
 */
fun makeClusterBitmap(count: Int, typeface: Typeface? = null, density: Float = 3f): Bitmap {
    val sizeDp = when {
        count >= 1000 -> 52f
        count >= 100 -> 46f
        count >= 10 -> 40f
        else -> 34f
    }
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val bmp = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f

    // 반투명 외곽 헤일로 (브랜드 그린)
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3322A75A }
    canvas.drawCircle(r, r, r, halo)
    // 흰색 외곽선 링 (핀과 통일감)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    canvas.drawCircle(r, r, r * 0.90f, ring)
    // 채워진 원 (브랜드 그린)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22A75A.toInt() }
    canvas.drawCircle(r, r, r * 0.80f, fill)
    // 개수 텍스트. 집계 클러스터는 수천 단위도 나오므로 자릿수에 따라 글자 크기를 줄여 원 안에 맞춘다.
    val label = clusterLabel(count)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * when (label.length) {
            1, 2 -> 0.34f
            3 -> 0.29f
            else -> 0.23f
        }
        this.typeface = typeface ?: Typeface.DEFAULT_BOLD
        isFakeBoldText = typeface == null
    }
    val ty = r - (tp.descent() + tp.ascent()) / 2f
    canvas.drawText(label, r, ty, tp)
    return bmp
}
