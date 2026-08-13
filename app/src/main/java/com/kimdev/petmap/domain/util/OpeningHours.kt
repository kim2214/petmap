package com.kimdev.petmap.domain.util

import java.time.LocalDateTime

/**
 * 운영시간/휴무일 문자열을 해석해 현재 영업 여부를 판단한다(베스트에포트).
 *
 * 지원 형식 예:
 *  - "매일 10:00~22:00", "10:00 - 21:00"
 *  - "월~금 09:00~18:00, 토 09:00~14:00"  (요일범위 + 복수 구간)
 *  - "월~수, 금 09:00~20:00"  (시간 없는 요일 세그먼트는 다음 세그먼트의 시간을 상속)
 *  - "토~일, 법정공휴일 08:30~13:30"  (공휴일 표기는 요일로 배정하지 않음)
 *  - "매일 15:00~11:00"  (자정 넘김)
 *  - "매일 00:00~24:00", "24시간", "상시 개방", "연중무휴"
 *  - "연중무휴 09:00~18:00"  (무휴 키워드가 있어도 시간표가 있으면 시간표를 따름)
 *  - "매일 11:00~21:00 브레이크타임 15:00~17:00"  (쉼표 유무와 무관, 키워드 뒤 구간은 휴게)
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

    // 키워드 뒤의 시간 구간은 영업이 아니라 휴게(그 시간엔 이용 불가)
    private val BREAK_KEYWORDS = listOf("브레이크", "휴게", "준비")

    // 정보성 표기 — 뒤의 시간은 영업 구간도 휴게 구간도 아니므로 무시
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
     * 세그먼트(쉼표 분리 단위) 하나의 파싱 결과.
     * 영업 구간은 첫 브레이크/정보성 키워드 "앞"에서만, 휴게 구간은 브레이크 키워드 "뒤"에서만
     * 추출한다 — 쉼표 없이 한 세그먼트에 섞여 있어도("매일 11:00~21:00 브레이크타임 15:00~17:00")
     * 영업 구간이 통째로 버려지거나 휴게로 오인되지 않는다.
     */
    private data class Segment(
        val raw: String,
        val openRanges: List<Pair<Int, Int>>,
        val openTexts: List<String>,
        val breakRanges: List<Pair<Int, Int>>,
        val breakTexts: List<String>,
        val alwaysOpen: Boolean,
    )

    private fun MatchResult.toMinutes(): Pair<Int, Int> =
        (groupValues[1].toInt() * 60 + groupValues[2].toInt()) to
            (groupValues[3].toInt() * 60 + groupValues[4].toInt())

    /** 세그먼트에서 첫 브레이크/정보성 키워드 앞까지 = 영업 시간대 파트 */
    private fun operatingPart(seg: String): String {
        val cut = (BREAK_KEYWORDS + INFO_KEYWORDS)
            .mapNotNull { k -> seg.indexOf(k).takeIf { it >= 0 } }
            .minOrNull() ?: return seg
        return seg.substring(0, cut)
    }

    /** 브레이크/휴게 키워드 각각의 바로 뒤에 오는 첫 시간 범위 */
    private fun breakMatches(seg: String): List<MatchResult> =
        BREAK_KEYWORDS.mapNotNull { k ->
            val idx = seg.indexOf(k)
            if (idx < 0) null else TIME.find(seg, idx)
        }

    private fun parseSegments(operatingTime: String): List<Segment> {
        val segs = operatingTime.split(",").map { seg ->
            val open = operatingPart(seg)
            val openMatches = TIME.findAll(open).toList()
            val breaks = breakMatches(seg)
            Segment(
                raw = seg,
                openRanges = openMatches.map { it.toMinutes() },
                openTexts = openMatches.map { it.value },
                breakRanges = breaks.map { it.toMinutes() },
                breakTexts = breaks.map { it.value },
                alwaysOpen = openMatches.isEmpty() && ALWAYS_OPEN_TABLE.any { open.contains(it) },
            )
        }.toMutableList()
        // "월~수, 금 09:00~20:00" / "토~일, 법정공휴일 08:30~13:30" — 요일만 있고 시간이 없는
        // 세그먼트는 뒤 세그먼트의 시간을 상속한다(실 데이터의 약 6%가 이 표기).
        for (i in segs.indices.reversed()) {
            val s = segs[i]
            val empty = s.openRanges.isEmpty() && !s.alwaysOpen && s.breakRanges.isEmpty()
            if (empty && i + 1 < segs.size && hasDaySignal(s.raw)) {
                val next = segs[i + 1]
                segs[i] = s.copy(
                    openRanges = next.openRanges,
                    openTexts = next.openTexts,
                    alwaysOpen = next.alwaysOpen,
                )
            }
        }
        return segs
    }

    /**
     * 운영시간 원문을 요일별 표로 파싱한다(상세 화면 표시용).
     * 어느 요일에도 시간을 배정할 수 없으면(자유 서식·공휴일 전용 등) null —
     * 호출부는 원문을 그대로 표시한다.
     */
    fun weeklySchedule(operatingTime: String?, closedDays: String?): List<DayHours>? {
        if (operatingTime.isNullOrBlank()) return null
        if (!TIME.containsMatchIn(operatingTime) &&
            ALWAYS_OPEN_TABLE.none { operatingTime.contains(it) }
        ) {
            return null
        }
        val segments = parseSegments(operatingTime)
        val table = (0..6).map { day ->
            val closed = closedDays != null && !closedDays.contains("무휴") &&
                isClosedToday(closedDays, day)
            val applies = segments.filter { segmentAppliesToDay(it.raw, day) }
            val hours = applies.flatMap {
                if (it.alwaysOpen) listOf(ALWAYS_OPEN_LABEL) else it.openTexts
            }.distinct()
            DayHours(
                day = day,
                hours = hours.takeIf { it.isNotEmpty() }?.joinToString(", "),
                breaks = applies.flatMap { it.breakTexts }.distinct(),
                isClosed = closed,
            )
        }
        return if (table.all { it.hours == null }) null else table
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
        val segments = parseSegments(operatingTime).filter { segmentAppliesToDay(it.raw, today) }

        // 브레이크/휴게 구간이 영업 구간과 겹치므로 먼저 확인한다.
        if (segments.any { s -> s.breakRanges.any { inTimeRange(nowMin, it.first, it.second) } }) {
            return false
        }
        for (s in segments) {
            if (s.alwaysOpen) return true
            if (s.openRanges.any { inTimeRange(nowMin, it.first, it.second) }) return true
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

    /** 요일 신호(매일/평일/주말/요일 문자)가 있는지. "공휴일"의 '일'은 요일이 아니므로 제거 후 판단. */
    private fun hasDaySignal(seg: String): Boolean {
        val s = seg.replace("공휴일", "")
        if (s.contains("매일") || s.contains("평일") || s.contains("주말")) return true
        return dayLetters(s).isNotEmpty()
    }

    /** 시간 표기 앞의 요일 문자들 (시간이 없으면 세그먼트 전체에서) */
    private fun dayLetters(s: String): String {
        val digitIdx = s.indexOfFirst { it.isDigit() }
        val dayPart = if (digitIdx >= 0) s.substring(0, digitIdx) else s
        return dayPart.filter { it in WD }
    }

    private fun segmentAppliesToDay(seg: String, day: Int): Boolean {
        // "공휴일"의 '일'이 요일 추출에서 일요일로 오인되지 않게 제거하되,
        // "주말·공휴일 10:00~18:00"처럼 요일과 병기된 경우는 요일 쪽으로 판정한다.
        val hasHoliday = seg.contains("공휴일")
        val s = seg.replace("공휴일", "")
        if (s.contains("매일")) return true
        if (s.contains("평일")) return day in 0..4
        if (s.contains("주말")) return day in 5..6
        DAY_RANGE.find(s)?.let { m ->
            val a = WD.indexOf(m.groupValues[1][0])
            val b = WD.indexOf(m.groupValues[2][0])
            return if (a <= b) day in a..b else (day >= a || day <= b)
        }
        val days = dayLetters(s)
        if (days.isNotEmpty()) return days.any { WD.indexOf(it) == day }
        // 요일 표기가 전혀 없으면: 공휴일 전용 구간은 특정 요일이 아니므로 제외, 그 외엔 매일로 간주
        return !hasHoliday
    }

    private fun inTimeRange(now: Int, open: Int, close: Int): Boolean =
        if (close > open) now in open until close
        else now >= open || now < close // 자정을 넘기는 영업시간
}
