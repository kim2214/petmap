package com.kimdev.petmap.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kimdev.petmap.domain.util.OpeningHours
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 현재 영업 여부. 재구성마다 정규식 파싱을 반복하지 않도록 캐시하되,
 * 분이 바뀌면 재계산해 화면을 켜 둔 채 마감 시각을 넘겨도 배지가 낡지 않는다.
 */
@Composable
fun rememberIsOpenNow(operatingTime: String?, closedDays: String?): Boolean? {
    var nowMinute by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
            nowMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    return remember(operatingTime, closedDays, nowMinute) {
        OpeningHours.isOpenNow(operatingTime, closedDays, nowMinute)
    }
}
