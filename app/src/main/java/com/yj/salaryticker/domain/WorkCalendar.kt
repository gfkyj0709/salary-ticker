package com.yj.salaryticker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Counts working days automatically (Mon–Fri minus Korean public holidays).
 * 근무일 자동 계산: 평일(월~금) − 공휴일. 사용자가 일수를 넣을 필요 없음.
 */
object WorkCalendar {

    // Korean public holidays incl. substitute holidays / 대체공휴일 포함 공휴일 목록
    // [확인필요] 정부 발표(임시공휴일 등)에 따라 매년 갱신 필요. 목록 없는 해는 평일만 계산.
    private val HOLIDAYS: Set<LocalDate> = setOf(
        "2026-01-01",                             // 신정 / New Year
        "2026-02-16", "2026-02-17", "2026-02-18", // 설날 / Lunar New Year
        "2026-03-02",                             // 삼일절 대체 / Independence Day (sub)
        "2026-05-05",                             // 어린이날 / Children's Day
        "2026-05-25",                             // 부처님오신날 대체 / Buddha's Birthday (sub)
        "2026-06-03",                             // 지방선거 / Local election day
        "2026-08-17",                             // 광복절 대체 / Liberation Day (sub)
        "2026-09-24", "2026-09-25",               // 추석 (26일은 토요일) / Chuseok
        "2026-10-05",                             // 개천절 대체 / Foundation Day (sub)
        "2026-10-09",                             // 한글날 / Hangul Day
        "2026-12-25",                             // 성탄절 / Christmas
    ).map(LocalDate::parse).toSet()               // Parse ISO dates / 날짜 파싱

    /**
     * Whether the given date is a working day.
     * 해당 날짜가 근무일인지.
     *
     * @param d 날짜
     * @return 평일이고 공휴일이 아니면 true
     */
    fun isWorkday(d: LocalDate): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY &&   // Not Saturday / 토요일 아님
        d.dayOfWeek != DayOfWeek.SUNDAY &&     // Not Sunday / 일요일 아님
        d !in HOLIDAYS                         // Not a holiday / 공휴일 아님

    /**
     * Working days in the month containing [date].
     * 해당 월의 실제 근무일 수 (월급 ÷ 이번 달 근무일 → 달마다 초당 급여가 조금씩 달라짐).
     *
     * @param date 기준 날짜
     * @return 근무일 수 (최소 1)
     */
    fun workDaysInMonth(date: LocalDate): Int {
        val ym = YearMonth.from(date)                                     // Target month / 대상 월
        return (1..ym.lengthOfMonth()).count { isWorkday(ym.atDay(it)) } // Count workdays / 근무일 세기
            .coerceAtLeast(1)                                             // Avoid divide-by-zero / 0 나누기 방지
    }
}
