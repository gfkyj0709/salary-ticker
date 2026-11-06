package com.yj.salaryticker.domain

import com.yj.salaryticker.data.PayType
import com.yj.salaryticker.data.SalarySettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's commute status.
 * 오늘의 근무 상태.
 */
enum class WorkStatus { OFF_DAY, BEFORE, WORKING, LUNCH, AFTER } // 휴무일 / 출근 전 / 근무 중 / 점심 / 퇴근 후

/**
 * Pure salary math (no Android dependency → unit-testable on JVM).
 * 순수 계산 로직 (안드로이드 의존 없음 → JVM 단위테스트 가능). Java의 static 유틸 클래스와 같은 object.
 *
 * 정규 근무: 출퇴근 시간표로 자동 계산 (버튼 불필요).
 * 야근: 버튼으로 기록한 구간 × 통상시급 × 1.5, 22~06시는 +0.5.
 */
object SalaryCalculator {

    private const val MIN_MS = 60_000L               // 1 minute / 1분
    private const val HOUR_MS = 3_600_000.0          // 1 hour / 1시간
    const val ORDINARY_HOURS_PER_MONTH = 209.0       // 월 소정근로 209h (주40h+주휴) [확인필요] 주 40시간 기준
    const val OVERTIME_RATE = 1.5                    // 연장근로 가산 50% / Overtime premium
    const val NIGHT_EXTRA = 0.5                      // 야간근로(22~06) 추가 50% / Night premium
    var zone: ZoneId = ZoneId.systemDefault()        // Time zone (overridable in tests) / 시간대 (테스트에서 교체 가능)

    /**
     * Monthly pay from annual or monthly input.
     * 입력이 연봉이면 ÷12, 월급이면 그대로.
     *
     * @param s 급여 설정
     * @return 월 급여(원)
     */
    fun monthlyPay(s: SalarySettings): Double = when (s.payType) {
        PayType.ANNUAL -> s.payAmount / 12.0      // Annual → monthly / 연봉 → 월급
        PayType.MONTHLY -> s.payAmount.toDouble() // Already monthly / 월급 그대로
    }

    /**
     * Overlap length of two ranges.
     * 두 구간 [a1,a2), [b1,b2) 의 겹치는 길이.
     *
     * @return 겹친 길이 (없으면 0)
     */
    private fun overlap(a1: Long, a2: Long, b1: Long, b2: Long): Long =
        (minOf(a2, b2) - maxOf(a1, b1)).coerceAtLeast(0L) // Intersection / 교집합 길이

    /**
     * Regular work length per day (schedule minus lunch inside it).
     * 하루 정규 근무시간 = (퇴근 − 출근) − 그 안의 점심시간.
     *
     * @param s 급여 설정
     * @return 하루 근무 ms
     */
    fun dailyWorkMs(s: SalarySettings): Long {
        val span = (s.endMin - s.startMin).coerceAtLeast(0) * MIN_MS                       // Office span / 출퇴근 사이
        val lunch = overlap(s.startMin.toLong(), s.endMin.toLong(),
            s.lunchStartMin.toLong(), (s.lunchStartMin + s.lunchMin).toLong()) * MIN_MS     // Lunch inside span / 점심 겹침
        return span - lunch                                                                // Net work / 순 근무
    }

    /**
     * Won per second for the month of [date].
     * 초당 급여 = 월급 ÷ (그 달 근무일 × 하루 근무시간).
     *
     * @param s 급여 설정
     * @param date 기준 날짜
     * @return 초당 급여(원)
     */
    fun ratePerSecond(s: SalarySettings, date: LocalDate = LocalDate.now(zone)): Double {
        val sec = WorkCalendar.workDaysInMonth(date) * dailyWorkMs(s) / 1000.0 // Monthly work seconds / 월 근무 초
        if (s.payAmount <= 0L || sec <= 0.0) return 0.0                        // Guard invalid input / 잘못된 입력 방어
        return monthlyPay(s) / sec                                             // Core formula / 핵심 공식
    }

    /**
     * Ordinary hourly wage (통상시급) used for overtime.
     * 통상시급 = 월급 ÷ 209.
     *
     * @param s 급여 설정
     * @return 통상시급(원)
     */
    fun ordinaryHourly(s: SalarySettings): Double = monthlyPay(s) / ORDINARY_HOURS_PER_MONTH // Monthly ÷ 209 / 월급÷209

    /** @return 해당 날짜 0시의 epoch ms */
    private fun dayStartMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli() // Midnight / 자정

    /**
     * Regular work done on [date] up to [nowMs] (past days = full day).
     * 해당 날짜에 [nowMs]까지 한 정규 근무 ms (지난 날은 하루 전체).
     *
     * @param s 급여 설정
     * @param date 날짜
     * @param nowMs 현재 epoch ms
     * @return 근무 ms
     */
    fun regularWorkedMs(s: SalarySettings, date: LocalDate, nowMs: Long): Long {
        if (!WorkCalendar.isWorkday(date)) return 0L                                     // Day off / 휴무일
        val d0 = dayStartMs(date)                                                        // Midnight / 자정
        val start = d0 + s.startMin * MIN_MS                                             // Clock-in / 출근
        val end = d0 + s.endMin * MIN_MS                                                 // Clock-out / 퇴근
        val ls = (d0 + s.lunchStartMin * MIN_MS).coerceIn(start, end)                    // Lunch start clipped / 점심 시작(구간 내)
        val le = (d0 + (s.lunchStartMin + s.lunchMin) * MIN_MS).coerceIn(start, end)     // Lunch end clipped / 점심 끝(구간 내)
        return overlap(start, end, Long.MIN_VALUE, nowMs) - overlap(ls, le, Long.MIN_VALUE, nowMs) // Work − lunch / 근무 − 점심
    }

    /**
     * Regular earnings on [date] up to [nowMs].
     * 해당 날짜 정규 근무 수입.
     *
     * @param s 급여 설정
     * @param date 날짜
     * @param nowMs 현재 epoch ms
     * @return 금액(원)
     */
    fun regularEarnedOn(s: SalarySettings, date: LocalDate, nowMs: Long): Double =
        regularWorkedMs(s, date, nowMs) / 1000.0 * ratePerSecond(s, date) // ms → won / 금액 환산

    /**
     * Regular earnings from [from] to today (inclusive).
     * [from]부터 오늘까지 정규 근무 수입 합계 (주간·월간 랭킹용).
     *
     * @param s 급여 설정
     * @param from 시작 날짜
     * @param nowMs 현재 epoch ms
     * @return 합계(원)
     */
    fun regularEarnedSince(s: SalarySettings, from: LocalDate, nowMs: Long): Double {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()  // Today / 오늘
        var d = from                                                        // Cursor / 반복 날짜
        var sum = 0.0                                                       // Total / 합계
        while (!d.isAfter(today)) { sum += regularEarnedOn(s, d, nowMs); d = d.plusDays(1) } // Sum days / 날짜별 합산
        return sum
    }

    /**
     * Paid regular time inside a slacking segment (lunch / after-hours excluded automatically).
     * 루팡 구간 중 실제 '돈 받는 근무시간'만 계산 (점심·퇴근 후는 자동 제외).
     *
     * @param s 급여 설정
     * @param startMs 루팡 시작
     * @param endMs 루팡 끝
     * @return 유급 근무 ms
     */
    fun paidMsBetween(s: SalarySettings, startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L                                                  // Empty / 빈 구간
        val d = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()                 // Day of start / 시작한 날
        return regularWorkedMs(s, d, endMs) - regularWorkedMs(s, d, startMs)              // Worked(end) − worked(start) / 차이
    }

    /**
     * Won "earned while slacking" for a segment.
     * 루팡 구간에 회사 돈으로 번 금액.
     *
     * @param s 급여 설정
     * @param startMs 루팡 시작
     * @param endMs 루팡 끝
     * @return 금액(원)
     */
    fun slackPay(s: SalarySettings, startMs: Long, endMs: Long): Double {
        val d = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()                 // Day / 날짜
        return paidMsBetween(s, startMs, endMs) / 1000.0 * ratePerSecond(s, d)            // ms × rate / 시간 × 초당급여
    }

    /**
     * Night (22:00–06:00) overlap inside [startMs, endMs).
     * 구간 안의 야간(22~06시) 시간.
     *
     * @param startMs 시작
     * @param endMs 끝
     * @return 야간 ms
     */
    fun nightMs(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L                                                   // Empty / 빈 구간
        var d = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().minusDays(1)     // From day before / 전날부터
        val last = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()                 // Until end day / 끝나는 날까지
        var sum = 0L                                                                      // Total / 합계
        while (!d.isAfter(last)) {
            val n1 = dayStartMs(d) + 22 * 60 * MIN_MS                                     // 22:00 that day / 당일 22시
            val n2 = dayStartMs(d.plusDays(1)) + 6 * 60 * MIN_MS                          // 06:00 next day / 다음날 6시
            sum += overlap(startMs, endMs, n1, n2)                                        // Add overlap / 겹침 합산
            d = d.plusDays(1)                                                             // Next day / 다음 날
        }
        return sum
    }

    /**
     * Legal overtime pay owed for one segment.
     * 한 구간의 법정 연장수당 = 통상시급 × (1.5 × 전체시간 + 0.5 × 야간시간).
     *
     * @param startMs 시작
     * @param endMs 끝
     * @param hourly 통상시급
     * @return 받아야 할 금액(원)
     */
    fun overtimePay(startMs: Long, endMs: Long, hourly: Double): Double {
        if (endMs <= startMs || hourly <= 0.0) return 0.0                                  // Nothing / 없음
        val total = endMs - startMs                                                        // Whole segment / 전체
        return hourly * (OVERTIME_RATE * total + NIGHT_EXTRA * nightMs(startMs, endMs)) / HOUR_MS // Formula / 공식
    }

    /**
     * Current status for the counter screen.
     * 카운터 화면용 현재 상태.
     *
     * @param s 급여 설정
     * @param nowMs 현재 epoch ms
     * @return 근무 상태
     */
    fun status(s: SalarySettings, nowMs: Long): WorkStatus {
        val t = Instant.ofEpochMilli(nowMs).atZone(zone)                                 // Now / 현재
        if (!WorkCalendar.isWorkday(t.toLocalDate())) return WorkStatus.OFF_DAY         // Day off / 휴무일
        val m = t.hour * 60 + t.minute                                                   // Minute of day / 하루 중 분
        return when {
            m < s.startMin -> WorkStatus.BEFORE                                          // Before work / 출근 전
            m >= s.endMin -> WorkStatus.AFTER                                            // After work / 퇴근 후
            m >= s.lunchStartMin && m < s.lunchStartMin + s.lunchMin -> WorkStatus.LUNCH // Lunch / 점심
            else -> WorkStatus.WORKING                                                   // Working / 근무 중
        }
    }

    /**
     * Millis until today's clock-out (0 if passed).
     * 오늘 퇴근까지 남은 ms.
     *
     * @param s 급여 설정
     * @param nowMs 현재 epoch ms
     * @return 남은 ms
     */
    fun msUntilClockOut(s: SalarySettings, nowMs: Long): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()  // Today / 오늘
        return (dayStartMs(today) + s.endMin * MIN_MS - nowMs).coerceAtLeast(0L) // Remaining / 남은 시간
    }
}
