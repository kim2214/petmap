package com.kimdev.petmap.domain.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 운영시간/휴무일 문자열을 해석해 현재 영업 여부를 판단한다(베스트에포트).
 *
 * 지원 형식 예:
 *  - "매일 10:00~22:00", "10:00 - 21:00"
 *  - "월~금 09:00~18:00, 토 09:00~14:00"  (요일범위 + 복수 구간)
 *  - "월~수, 금 09:00~20:00"  (시간 없는 요일 세그먼트는 다음 세그먼트의 시간을 상속)
 *  - "토~일, 법정공휴일 08:30~13:30"  (공휴일 표기는 요일로 배정하지 않음)
 *  - "매일 15:00~11:00", "18:00~25:00"  (자정 넘김 — 넘긴 부분은 시작한 요일의 영업으로 본다)
 *  - "월~금 10:00~19:00, 토 변동"  (시간 없는 요일 세그먼트는 그 요일 판단불가, "금 휴진"이면 휴무)
 *  - "매일 00:00~24:00", "24시간", "상시 개방", "연중무휴"
 *  - "연중무휴 09:00~18:00"  (무휴 키워드가 있어도 시간표가 있으면 시간표를 따름)
 *  - "매일 11:00~21:00 브레이크타임 15:00~17:00"  (쉼표 유무와 무관, 키워드 뒤 구간은 휴게)
 * 휴무일 예: "매주 일요일", "매주 토, 일", "연중무휴", "법정공휴일",
 *  "2, 4째주 일요일"·"첫째 셋째주 토요일"·"매달 마지막 주 토요일"(날짜로 판정), "격주 일요일"(판단 안 함)
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

    // 시간 없는 요일 세그먼트가 이 표기를 담으면 그 요일은 휴무("월~토 10:00~20:00, 금 휴진")
    private val CLOSED_KEYWORDS = listOf("휴무", "휴진", "휴관", "휴업", "정기휴일")

    private const val DAY_MINUTES = 24 * 60

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
    ) {
        /** 영업 시간도 상시 표기도 없다 — "토 변동"처럼 그 요일 영업 여부를 알 수 없거나, [isClosedMarker] */
        val isTimeless: Boolean get() = openRanges.isEmpty() && !alwaysOpen

        /** "금 휴진" — 시간 없이 휴무만 적은 세그먼트 */
        val isClosedMarker: Boolean get() = isTimeless && CLOSED_KEYWORDS.any { raw.contains(it) }
    }

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
            val empty = s.isTimeless && s.breakRanges.isEmpty() && !s.isClosedMarker && !s.raw.contains("변동")
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
            val applies = segments.filter { segmentAppliesToDay(it.raw, day) }
            // n째주·마지막 주 휴무는 날짜마다 달라 요일 표에 휴무로 적지 않는다(원문 휴무일 표기가 함께 보인다)
            val closed = closedRules(closedDays).any { it.day == day && it.kind == ClosedKind.WEEKLY } ||
                applies.any { it.isClosedMarker }
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
        val date = now.toLocalDate()
        val today = date.dayOfWeek.value - 1 // 월=0 .. 6=일
        val yesterday = (today + 6) % 7
        val rules = closedRules(closedDays)
        val closedToday = rules.any { it.closesOn(date) }

        if (operatingTime.isNullOrBlank()) return if (closedToday) false else null
        if (!TIME.containsMatchIn(operatingTime)) {
            if (closedToday) return false
            return if (ALWAYS_OPEN.any { operatingTime.contains(it) }) true else null
        }

        val nowMin = now.hour * 60 + now.minute
        val segments = parseSegments(operatingTime)

        // 어젯밤 시작해 자정을 넘긴 영업("금 18:00~02:00" 의 토요일 새벽)은 어제 요일·휴무 기준으로 본다.
        // 오늘이 휴무여도 어젯밤 영업의 연장이면 영업중이다.
        if (rules.none { it.closesOn(date.minusDays(1)) }) {
            val spill = segments.filter { segmentAppliesToDay(it.raw, yesterday) }
                .flatMap { it.openRanges }
                .any { (open, close) -> nowMin < spillMinutes(open, close) }
            if (spill) return true
        }
        if (closedToday) return false

        val todays = segments.filter { segmentAppliesToDay(it.raw, today) }
        if (todays.any { it.isClosedMarker }) return false
        // 브레이크/휴게 구간이 영업 구간과 겹치므로 먼저 확인한다.
        if (todays.any { s -> s.breakRanges.any { inTimeRange(nowMin, it.first, it.second) } }) {
            return false
        }
        for (s in todays) {
            if (s.alwaysOpen) return true
            if (s.openRanges.any { (open, close) -> nowMin >= open && nowMin < todayEnd(open, close) }) return true
        }
        // 오늘 해당하는 세그먼트가 "토 변동"처럼 시간 없는 것뿐이면 판단할 수 없다
        if (todays.isNotEmpty() && todays.all { it.isTimeless }) return null
        // 시간표는 있으나 지금은 영업 구간 밖(오늘 해당 없음 포함)
        return false
    }

    /** 자정을 넘기는 구간인지 — "22:00~02:00", "18:00~25:00", "00:00~00:00"(종일) */
    private fun crossesMidnight(open: Int, close: Int) = close <= open || close > DAY_MINUTES

    /** 시작한 날 안에서의 종료 시각(분) */
    private fun todayEnd(open: Int, close: Int) = if (crossesMidnight(open, close)) DAY_MINUTES else close

    /** 다음 날 새벽으로 넘어간 부분의 종료 시각(분). 넘기지 않으면 0 */
    private fun spillMinutes(open: Int, close: Int) = when {
        close > DAY_MINUTES -> close - DAY_MINUTES
        close <= open -> close
        else -> 0
    }

    private enum class ClosedKind { WEEKLY, NTH_WEEK, LAST_WEEK, IRREGULAR }

    /** 휴무 규칙 하나: [day] 요일(0=월)이 [kind] 방식으로 쉰다. NTH_WEEK 면 [weeks] 번째 그 요일 */
    private data class ClosedRule(val day: Int, val kind: ClosedKind, val weeks: Set<Int> = emptySet()) {
        fun closesOn(date: LocalDate): Boolean {
            if (date.dayOfWeek.value - 1 != day) return false
            val nth = (date.dayOfMonth - 1) / 7 + 1 // 이 달의 몇 번째 그 요일인지
            return when (kind) {
                ClosedKind.WEEKLY -> true
                ClosedKind.NTH_WEEK -> nth in weeks
                ClosedKind.LAST_WEEK -> date.dayOfMonth + 7 > date.lengthOfMonth()
                ClosedKind.IRREGULAR -> false // 격주·변동은 어느 날인지 알 수 없어 휴무로 단정하지 않는다
            }
        }
    }

    // "일요일" 또는 구분자로 둘러싸인 한 글자 요일("매주 토, 일"). "1월1일"·"공휴일"의 월/일은 제외된다.
    private val CLOSED_DAY = Regex("""([월화수목금토일])요일|(?<![^\s,/·(])([월화수목금토일])(?![^\s,/·)])""")

    // "2, 4째주"·"1째주, 3째주"·"2, 4번째" 의 한 자리 숫자 ("매월 10일, 4째주" 의 10 은 날짜라 제외)
    private val ORDINAL_DIGIT = Regex("""(?<!\d)([1-5])(?!\d)(?=[\d\s,·째주번]*(?:째|번째))""")
    private val ORDINAL_WORD = Regex("""(첫|둘|셋|넷|다섯)\s*(?:째|번째)""")
    private val ORDINAL_WORDS = listOf("첫", "둘", "셋", "넷", "다섯")
    private val SEPARATORS_ONLY = Regex("""[\s,/·]*""")

    /**
     * 휴무일 원문을 요일별 규칙으로 나눈다. 요일 앞의 문맥(직전 요일 이후 텍스트)으로 방식을 정한다:
     * "2, 4째주"·"첫째"·"2번째" → n째주, "마지막" → 마지막 주, "격주"·"변동" → 판단 안 함, 그 밖은 매주.
     * "격주 일, 월"·"매주 토, 일" 처럼 구분자만 사이에 둔 요일은 앞 요일의 방식을 이어받는다.
     * 요일 뒤에 "(넷째주 제외)"·"변동" 이 붙으면 판단하지 않는다.
     */
    private fun closedRules(closedDays: String?): List<ClosedRule> {
        if (closedDays.isNullOrBlank() || closedDays.contains("무휴")) return emptyList()
        val rules = mutableListOf<ClosedRule>()
        var prevEnd = 0
        var prev: ClosedRule? = null
        for (m in CLOSED_DAY.findAll(closedDays)) {
            val letter = (m.groups[1] ?: m.groups[2])!!.value[0]
            val context = closedDays.substring(prevEnd, m.range.first)
            val after = closedDays.substring(m.range.last + 1).substringBefore(',')
            val qualified = closedKindOf(context)
            var rule = when {
                qualified != null -> qualified.copy(day = WD.indexOf(letter))
                prev != null && SEPARATORS_ONLY.matches(context) -> prev.copy(day = WD.indexOf(letter))
                else -> ClosedRule(WD.indexOf(letter), ClosedKind.WEEKLY)
            }
            if (after.contains("제외") || after.contains("변동")) rule = rule.copy(kind = ClosedKind.IRREGULAR)
            rules += rule
            prev = rule
            prevEnd = m.range.last + 1
        }
        return rules
    }

    /** 요일 앞 문맥의 휴무 방식. 방식을 나타내는 표기가 없으면 null(매주 또는 앞 요일 상속) */
    private fun closedKindOf(context: String): ClosedRule? {
        if (context.contains("격주") || context.contains("변동")) return ClosedRule(0, ClosedKind.IRREGULAR)
        if (context.contains("마지막")) return ClosedRule(0, ClosedKind.LAST_WEEK)
        val weeks = ORDINAL_DIGIT.findAll(context).map { it.groupValues[1].toInt() }.toSet() +
            ORDINAL_WORD.findAll(context).map { ORDINAL_WORDS.indexOf(it.groupValues[1]) + 1 }.toSet()
        if (weeks.isNotEmpty()) return ClosedRule(0, ClosedKind.NTH_WEEK, weeks)
        if (context.contains("매주")) return ClosedRule(0, ClosedKind.WEEKLY)
        return null
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
