package com.kimdev.petmap.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 영업중 파서 테스트.
 * 기준 요일: 2024-06-24=월, 25=화, 26=수, 27=목, 28=금, 29=토, 30=일
 */
class OpeningHoursTest {

    private fun at(day: Int, hour: Int, min: Int = 0) = LocalDateTime.of(2024, 6, day, hour, min)

    @Test fun `매일 영업시간 내면 영업중`() {
        assertEquals(true, OpeningHours.isOpenNow("매일 10:00~22:00", null, at(24, 15)))
    }

    @Test fun `매일 영업시간 밖이면 종료`() {
        assertEquals(false, OpeningHours.isOpenNow("매일 10:00~22:00", null, at(24, 23)))
    }

    @Test fun `시간 형식 하이픈도 인식`() {
        assertEquals(true, OpeningHours.isOpenNow("10:00 - 21:00", null, at(24, 12)))
    }

    @Test fun `요일범위 - 평일 영업, 토요일은 종료`() {
        assertEquals(true, OpeningHours.isOpenNow("월~금 09:00~18:00", null, at(24, 10)))   // 월
        assertEquals(false, OpeningHours.isOpenNow("월~금 09:00~18:00", null, at(29, 10)))  // 토
    }

    @Test fun `복수 구간 - 토요일 별도 시간 적용`() {
        val ot = "월~금 09:00~19:00, 토 09:00~14:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(29, 10)))   // 토 10시 → 영업
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(29, 16)))  // 토 16시 → 종료
    }

    @Test fun `화부터 일까지 - 월요일은 종료`() {
        assertEquals(false, OpeningHours.isOpenNow("화~일 09:00~18:00", null, at(24, 10))) // 월
        assertEquals(true, OpeningHours.isOpenNow("화~일 09:00~18:00", null, at(25, 10)))  // 화
    }

    @Test fun `자정 넘김 영업시간`() {
        val ot = "매일 22:00~02:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 23)))   // 23시
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 1)))    // 01시
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 12)))  // 12시
    }

    @Test fun `24시간 상시 연중 표기는 항상 영업`() {
        assertEquals(true, OpeningHours.isOpenNow("매일 00:00~24:00", null, at(24, 3)))
        assertEquals(true, OpeningHours.isOpenNow("24시간", null, at(24, 3)))
        assertEquals(true, OpeningHours.isOpenNow("상시 개방", null, at(24, 3)))
    }

    @Test fun `휴무 요일이면 종료`() {
        assertEquals(false, OpeningHours.isOpenNow("매일 10:00~22:00", "매주 일요일", at(30, 12))) // 일
        assertEquals(true, OpeningHours.isOpenNow("매일 10:00~22:00", "매주 일요일", at(24, 12)))  // 월
    }

    @Test fun `연중무휴는 휴무 없음`() {
        assertEquals(true, OpeningHours.isOpenNow("매일 10:00~22:00", "연중무휴", at(30, 12)))
    }

    @Test fun `휴무일에 복수 요일 - 토 일 포함`() {
        assertEquals(false, OpeningHours.isOpenNow("매일 10:00~22:00", "매주 토, 일", at(29, 12))) // 토
        assertEquals(false, OpeningHours.isOpenNow("매일 10:00~22:00", "매주 토, 일", at(30, 12))) // 일
        assertEquals(true, OpeningHours.isOpenNow("매일 10:00~22:00", "매주 토, 일", at(28, 12)))  // 금
    }

    @Test fun `법정공휴일 표기는 요일 휴무로 오판하지 않음`() {
        // '법정공휴일'에 '일' 글자가 있어도 일요일 휴무로 처리하면 안 됨
        assertEquals(true, OpeningHours.isOpenNow("매일 10:00~22:00", "법정공휴일", at(30, 12)))
    }

    @Test fun `운영시간 정보 없으면 null`() {
        assertNull(OpeningHours.isOpenNow(null, "연중무휴", at(24, 12)))
        assertNull(OpeningHours.isOpenNow("", null, at(24, 12)))
    }

    @Test fun `연중무휴여도 시간표가 있으면 시간표를 따름`() {
        val ot = "연중무휴 09:00~18:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 10)))
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 3)))   // 새벽엔 종료
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 20)))
    }

    @Test fun `연중 키워드만 있고 시간표 없으면 상시 영업`() {
        assertEquals(true, OpeningHours.isOpenNow("연중무휴", null, at(24, 3)))
        assertEquals(true, OpeningHours.isOpenNow("연중 개방", null, at(24, 3)))
    }

    @Test fun `브레이크타임 구간에는 영업중 아님`() {
        val ot = "매일 11:00~21:00, 브레이크타임 15:00~17:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 13)))
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 16)))  // 브레이크
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 18)))
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 22))) // 마감 후
    }

    @Test fun `휴게시간 표기도 브레이크로 처리`() {
        val ot = "09:00~18:00, 휴게시간 12:00~13:00"
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 12, 30)))
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 14)))
    }

    @Test fun `라스트오더 표기는 영업 판정에 영향 없음`() {
        val ot = "매일 10:00~21:00, 라스트오더 20:00~20:30"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 20, 15))) // LO 중이어도 영업중
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 21, 30)))
    }

    @Test fun `세그먼트 단위 상시 표기 - 주말 24시간`() {
        val ot = "평일 09:00~18:00, 주말 24시간"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(29, 3)))    // 토 새벽
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 3)))   // 월 새벽
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 10)))   // 월 오전
    }

    // ===== weeklySchedule (요일별 표) =====

    @Test fun `요일 표 - 요일범위와 별도 토요일 시간`() {
        val table = OpeningHours.weeklySchedule("월~금 09:00~19:00, 토 09:00~14:00", "매주 일요일")!!
        assertEquals("09:00~19:00", table[0].hours)  // 월
        assertEquals("09:00~19:00", table[4].hours)  // 금
        assertEquals("09:00~14:00", table[5].hours)  // 토
        assertEquals(true, table[6].isClosed)        // 일 = 정기휴무
    }

    @Test fun `요일 표 - 브레이크타임은 별도 필드로`() {
        val table = OpeningHours.weeklySchedule("매일 11:00~21:00, 브레이크타임 15:00~17:00", null)!!
        assertEquals("11:00~21:00", table[2].hours)
        assertEquals(listOf("15:00~17:00"), table[2].breaks)
    }

    @Test fun `요일 표 - 주말 24시간 세그먼트`() {
        val table = OpeningHours.weeklySchedule("평일 09:00~18:00, 주말 24시간", null)!!
        assertEquals("09:00~18:00", table[0].hours)  // 월
        assertEquals("24시간", table[5].hours)        // 토
    }

    @Test fun `요일 표 - 시간 패턴 없으면 null`() {
        assertNull(OpeningHours.weeklySchedule("연중무휴", null))
        assertNull(OpeningHours.weeklySchedule("문의 요망", null))
        assertNull(OpeningHours.weeklySchedule(null, null))
    }

    @Test fun `요일 표 - 24시간 키워드만 있으면 전 요일 24시간`() {
        val table = OpeningHours.weeklySchedule("24시간", null)!!
        assertEquals("24시간", table[0].hours)
        assertEquals("24시간", table[6].hours)
    }

    @Test fun `법정공휴일 구간을 일요일 시간으로 오인하지 않음`() {
        // "공휴일"의 '일' 글자가 요일 추출에서 일요일로 매칭되면 안 된다
        val ot = "토~목 10:00~22:00, 법정공휴일 10:00~19:00"
        val table = OpeningHours.weeklySchedule(ot, "매주 금요일")!!
        assertEquals("10:00~22:00", table[6].hours)  // 일 — 공휴일 구간 제외
        assertEquals(true, table[4].isClosed)        // 금 = 정기휴무
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(30, 20))) // 일 20시 → 영업중
    }

    @Test fun `공휴일이 요일과 병기되면 요일 쪽으로 판정`() {
        val ot = "주말·공휴일 10:00~18:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(29, 12)))  // 토 정오 → 영업중
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 12))) // 월 정오 → 종료
    }

    @Test fun `공휴일 전용 시간표만 있으면 표 대신 원문 폴백`() {
        // 어느 요일에도 배정 불가 → weeklySchedule null (호출부가 원문 표시)
        assertNull(OpeningHours.weeklySchedule("법정공휴일 10:00~19:00", null))
    }

    @Test fun `쉼표 없는 라스트오더 표기도 영업 구간을 잃지 않음`() {
        val ot = "매일 10:00~22:00 라스트오더 21:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 12)))
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 23)))
        assertEquals("10:00~22:00", OpeningHours.weeklySchedule(ot, null)!![0].hours)
    }

    @Test fun `쉼표 없는 브레이크타임 표기 - 영업 구간과 휴게 구간 분리`() {
        val ot = "매일 11:00~21:00 브레이크타임 15:00~17:00"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 13)))
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 16)))  // 브레이크
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 18)))
        val day = OpeningHours.weeklySchedule(ot, null)!![0]
        assertEquals("11:00~21:00", day.hours)
        assertEquals(listOf("15:00~17:00"), day.breaks)
    }

    @Test fun `시간 없는 요일 세그먼트는 다음 세그먼트의 시간을 상속`() {
        // 실 데이터 표기: "월~수, 금 09:00~20:00" = 월~수와 금 모두 09:00~20:00
        val ot = "월~수, 금 09:00~20:00, 목 09:00~20:30"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(24, 10)))   // 월 → 상속된 09~20
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(24, 21)))  // 월 21시 → 종료
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(27, 20, 15))) // 목 20:15 → 영업중
        val table = OpeningHours.weeklySchedule(ot, null)!!
        assertEquals("09:00~20:00", table[1].hours)   // 화
        assertEquals("09:00~20:30", table[3].hours)   // 목
        assertNull(table[5].hours)                     // 토 — 정보 없음
    }

    @Test fun `요일과 공휴일 나열이 섞인 상속 - 토~일, 법정공휴일`() {
        // "토~일, 법정공휴일 08:30~13:30": 토·일이 뒤 세그먼트의 시간을 상속하고
        // 공휴일 세그먼트 자체는 어느 요일에도 배정되지 않는다
        val ot = "월~금 08:40~18:00, 토~일, 법정공휴일 08:30~13:30"
        assertEquals(true, OpeningHours.isOpenNow(ot, null, at(29, 10)))   // 토 10시 → 영업중
        assertEquals(false, OpeningHours.isOpenNow(ot, null, at(29, 15)))  // 토 15시 → 종료
        val table = OpeningHours.weeklySchedule(ot, null)!!
        assertEquals("08:30~13:30", table[6].hours)   // 일
        assertEquals("08:40~18:00", table[0].hours)   // 월
    }
}
