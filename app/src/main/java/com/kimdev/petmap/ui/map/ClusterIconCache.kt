package com.kimdev.petmap.ui.map

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.kimdev.petmap.R
import com.naver.maps.map.overlay.OverlayImage

/**
 * 클러스터 버블 아이콘 캐시 (프로세스 수명).
 *
 * 이전에는 MapScreen 컴포지션 안의 LRU 였어서 탭을 옮겨 지도가 dispose 되면 통째로 버려지고,
 * 돌아올 때마다 메인 스레드에서 비트맵을 다시 구웠다. 키를 (라벨, 배율)로 두고 프로세스 전역에
 * 보관하면 재진입·회전에서 재생성이 없다. 라벨은 [clusterLabel] 이 버킷화하므로(100 이상은
 * 10 단위, 1000 이상은 100 단위) 개수가 제각각이어도 키 수가 수백을 넘지 않는다.
 */
object ClusterIconCache {
    private const val MAX_ENTRIES = 256

    private val cache = object : LinkedHashMap<String, OverlayImage>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OverlayImage>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(count: Int, typeface: Typeface?, density: Float): OverlayImage {
        // 같은 라벨·같은 크기 버킷은 같은 비트맵이다 (makeClusterBitmap 이 라벨과 크기 버킷만 본다)
        val key = "${clusterLabel(count)}|${clusterSizeDp(count)}|$density"
        return cache.getOrPut(key) {
            OverlayImage.fromBitmap(makeClusterBitmap(count, typeface, density))
        }
    }

    /** 테스트·메모리 압박 시 비우기 */
    @Synchronized
    fun clear() = cache.clear()
}

/** 현재 배율·브랜드 폰트로 묶인 클러스터 아이콘 조회 함수. */
@Composable
fun rememberClusterIconProvider(): (Int) -> OverlayImage {
    val context: Context = LocalContext.current
    val density = LocalDensity.current.density
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.nanum_square_round_extrabold) }
    return remember(density, typeface) {
        { count: Int -> ClusterIconCache.get(count, typeface, density) }
    }
}
