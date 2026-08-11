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
 *  - "연중무휴 09:00~18:00"  (무휴 키워드가 있어도 시간표가 있으면 시간표를 따름)
 *  - "매일 11:00~21:00, 브레이크타임 15:00~17:00"  (브레이크 구간은 영업중이 아님)
 * 휴무일 예: "매주 일요일", "매주 토, 일", "연중무휴", "법정공휴일"
 */
object OpeningHours {
    private const val WD = "월화수목금토일" // index 0..6 = 월(Mon)..일(Sun)

    private val TIME = Regex("""(\d{1,2}):(\d{2})\s*[~\-]\s*(\d{1,2}):(\d{2})""")
    private val DAY_RANGE = Regex("""([월화수목금토일])\s*~\s*([월화수목금토일])""")

    // "연중무휴"는 휴무 없음의 신호이지 24시간 영업의 신호가 아니므로 시간표가 없을 때만 상시 영업으로 본다.
    private val ALWAYS_OPEN = listOf("24시간", "상시", "연중")

    // 요일 표에 "24시간"으로 적어도 되는 키워드. "연중(무휴)"은 휴무 없음의 신호일 뿐
    // 24시간 영업이 아니므로 제외한다.
    private val ALWAYS_OPEN_TABLE = listOf("24시간", "상시")

    /** 요일 표에서 상시 영업 세그먼트를 나타내는 표기 */
    const val ALWAYS_OPEN_LABEL = "24시간"

    // 이 구간에 있으면 오히려 이용 불가(영업 구간으로 세면 안 됨)
    private val BREAK_KEYWORDS = listOf("브레이크", "휴게", "준비")

    // 정보성 표기 — 영업 구간도 휴게 구간도 아니므로 무시
    private val INFO_KEYWORDS = listOf("라스트오더", "주문마감", "입장마감")

    /**
     * 요일 하루치 표. [hours] 는 영업 구간 표기("09:00~18:00" 또는 "24시간"),
     * [breaks] 는 브레이크/휴게 구간, [isClosed] 는 정기휴무 여부.
     * hours 가 null 이고 휴무도 아니면 해당 요일 정보를 알 수 없다는 뜻.
     */
    data class DayHours(
        val day: Int, // 0=월 .. 6=일
        val hours: String?,
        val breaks: List<String> = emptyList(),
        val isClosed: Boolean = false,
    )

    /**
     * 운영시간 원문을 요일별 표로 파싱한다(상세 화면 표시용).
     * 시간 패턴이 전혀 없으면 표로 만들 수 없으므로 null (호출부는 원문을 그대로 표시).
     */
    fun weeklySchedule(operatingTime: String?, closedDays: String?): List<DayHours>? {
        if (operatingTime.isNullOrBlank()) return null
        if (!TIME.containsMatchIn(operatingTime) &&
            ALWAYS_OPEN_TABLE.none { operatingTime.contains(it) }
        ) {
            return null
        }
        val segments = operatingTime.split(",")
        return (0..6).map { day ->
            val closed = closedDays != null && !closedDays.contains("무휴") &&
                isClosedToday(closedDays, day)
            val hours = segments
                .filter { seg -> (BREAK_KEYWORDS + INFO_KEYWORDS).none { seg.contains(it) } }
                .filter { segmentAppliesToday(it, day) }
                .mapNotNull { seg ->
                    TIME.find(seg)?.value
                        ?: if (ALWAYS_OPEN_TABLE.any { seg.contains(it) }) ALWAYS_OPEN_LABEL else null
                }
            val breaks = segments
                .filter { seg -> BREAK_KEYWORDS.any { seg.contains(it) } }
                .filter { segmentAppliesToday(it, day) }
                .mapNotNull { TIME.find(it)?.value }
            DayHours(
                day = day,
                hours = hours.takeIf { it.isNotEmpty() }?.joinToString(", "),
                breaks = breaks,
                isClosed = closed,
            )
        }
    }

    /** true=영업중, false=영업종료/휴무, null=정보없음·판단불가 */
    fun isOpenNow(operatingTime: String?, closedDays: String?, now: LocalDateTime): Boolean? {
        val today = now.dayOfWeek.value - 1 // 월=0 .. 일=6

        if (closedDays != null && !closedDays.contains("무휴") && isClosedToday(closedDays, today)) {
            return false
        }

        if (operatingTime.isNullOrBlank()) return null
        if (!TIME.containsMatchIn(operatingTime)) {
            return if (ALWAYS_OPEN.any { operatingTime.contains(it) }) true else null
        }

        val nowMin = now.hour * 60 + now.minute
        val segments = operatingTime.split(",")

        // 브레이크/휴게 구간이 영업 구간과 겹치므로 먼저 확인한다.
        for (seg in segments) {
            if (BREAK_KEYWORDS.none { seg.contains(it) }) continue
            if (!segmentAppliesToday(seg, today)) continue
            val m = TIME.find(seg) ?: continue
            val open = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            val close = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (inTimeRange(nowMin, open, close)) return false
        }

        var anyForToday = false
        for (seg in segments) {
            if ((BREAK_KEYWORDS + INFO_KEYWORDS).any { seg.contains(it) }) continue
            if (!segmentAppliesToday(seg, today)) continue
            // "주말 24시간"처럼 시간 없이 상시 키워드만 있는 세그먼트
            if (!TIME.containsMatchIn(seg) && ALWAYS_OPEN.any { seg.contains(it) }) return true
            anyForToday = true
            val m = TIME.find(seg) ?: continue
            val open = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            val close = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (inTimeRange(nowMin, open, close)) return true
        }
        // 시간표는 있으나 지금은 영업 구간 밖(오늘 해당 없음 포함)
        return false
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
        // "법정공휴일 10:00~19:00" 같은 공휴일 전용 구간은 특정 요일이 아니다 —
        // "공휴일"의 '일' 글자가 아래 개별 요일 추출에서 일요일로 오인되는 것을 막는다.
        if (seg.contains("공휴일")) return false
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
