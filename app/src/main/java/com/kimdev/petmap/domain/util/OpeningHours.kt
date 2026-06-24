package com.kimdev.petmap.domain.util

import java.time.LocalDateTime

/**
 * 운영시간/휴무일 문자열을 해석해 현재 영업 여부를 판단한다(베스트에포트).
 *
 * 지원 형식 예:
 *  - "매일 10:00~22:00", "10:00 - 21:00"
 *  - "월~금 09:00~18:00, 토 09:00~14:00"  (요일범위 + 복수 구간)
 *  - "화~일 09:00~18:00"
 *  - "매일 15:00~11:00"  (자정 넘김)
 *  - "매일 00:00~24:00", "24시간", "상시 개방", "연중무휴"
 * 휴무일 예: "매주 일요일", "매주 토, 일", "연중무휴", "법정공휴일"
 */
object OpeningHours {
    private const val WD = "월화수목금토일" // index 0..6 = 월(Mon)..일(Sun)

    private val TIME = Regex("""(\d{1,2}):(\d{2})\s*[~\-]\s*(\d{1,2}):(\d{2})""")
    private val DAY_RANGE = Regex("""([월화수목금토일])\s*~\s*([월화수목금토일])""")

    /** true=영업중, false=영업종료/휴무, null=정보없음·판단불가 */
    fun isOpenNow(operatingTime: String?, closedDays: String?, now: LocalDateTime): Boolean? {
        val today = now.dayOfWeek.value - 1 // 월=0 .. 일=6

        if (closedDays != null && !closedDays.contains("무휴") && isClosedToday(closedDays, today)) {
            return false
        }

        if (operatingTime.isNullOrBlank()) return null
        if (operatingTime.contains("24시간") || operatingTime.contains("상시") ||
            operatingTime.contains("00:00~24:00") || operatingTime.contains("00:00-24:00") ||
            operatingTime.contains("연중")
        ) return true

        val nowMin = now.hour * 60 + now.minute
        var anyForToday = false
        for (seg in operatingTime.split(",")) {
            if (!segmentAppliesToday(seg, today)) continue
            anyForToday = true
            val m = TIME.find(seg) ?: continue
            val open = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            val close = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (inTimeRange(nowMin, open, close)) return true
        }
        return when {
            anyForToday -> false                        // 오늘 영업시간대가 있는데 그 밖
            TIME.containsMatchIn(operatingTime) -> false // 시간표는 있으나 오늘 해당 요일 아님
            else -> null                                 // 시간 패턴 자체가 없음
        }
    }

    private fun isClosedToday(closedDays: String, today: Int): Boolean {
        // "2,4째주 일요일" / "변동" 등 복잡/불확실 패턴은 휴무로 단정하지 않음
        if (closedDays.contains("째주") || closedDays.contains("변동")) return false
        val c = WD[today]
        if (closedDays.contains("${c}요일")) return true
        // "매주 토, 일" 처럼 구분자에 둘러싸인 단일 요일 문자
        return Regex("""(^|[\s,/·])$c([\s,/·]|요일|$)""").containsMatchIn(closedDays)
    }

    private fun segmentAppliesToday(seg: String, today: Int): Boolean {
        if (seg.contains("매일")) return true
        if (seg.contains("평일")) return today in 0..4
        if (seg.contains("주말")) return today in 5..6
        DAY_RANGE.find(seg)?.let { m ->
            val a = WD.indexOf(m.groupValues[1][0])
            val b = WD.indexOf(m.groupValues[2][0])
            return if (a <= b) today in a..b else (today >= a || today <= b)
        }
        // 시간 앞의 개별 요일 문자들
        val dayPart = seg.substringBefore(seg.firstOrNull { it.isDigit() } ?: ' ')
        val days = dayPart.filter { it in WD }
        if (days.isNotEmpty()) return days.any { WD.indexOf(it) == today }
        return true // 요일 표기가 없으면 매일로 간주
    }

    private fun inTimeRange(now: Int, open: Int, close: Int): Boolean =
        if (close > open) now in open until close
        else now >= open || now < close // 자정을 넘기는 영업시간
}
