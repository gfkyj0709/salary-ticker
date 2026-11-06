package com.yj.salaryticker.domain

import com.yj.salaryticker.data.PayType
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.ui.Format
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit tests for pay math (runs on CI with `gradle testDebugUnitTest`).
 * 급여 계산 단위테스트 (CI에서 실행).
 */
class SalaryCalculatorTest {

    private val seoul = ZoneId.of("Asia/Seoul")                                       // Fixed zone / 시간대 고정
    private val s = SalarySettings(payType = PayType.MONTHLY, payAmount = 4_200_000) // 월 420만, 09~18, 점심 12~13

    /** Fix time zone. / 시간대 고정. */
    @Before fun setUp() { SalaryCalculator.zone = seoul }

    /** @return 서울 기준 epoch ms */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(seoul).toInstant().toEpochMilli()

    /** 2026년 11월 근무일 21일. */
    @Test fun workdaysNov2026() = assertEquals(21, WorkCalendar.workDaysInMonth(LocalDate.of(2026, 11, 6)))

    /** 하루 근무 8시간 (9시간 − 점심 1시간). */
    @Test fun dailyWork() = assertEquals(8 * 3_600_000L, SalaryCalculator.dailyWorkMs(s))

    /** 초당 급여 = 420만 ÷ (21일 × 8h × 3600). */
    @Test fun rate() = assertEquals(4_200_000.0 / (21 * 8 * 3600), SalaryCalculator.ratePerSecond(s, LocalDate.of(2026, 11, 6)), 1e-9)

    /** 시간대별 정규 근무시간 (점심 제외, 주말 0). */
    @Test fun regularWorked() {
        val d = LocalDate.of(2026, 11, 6)                                                       // Friday / 금요일
        assertEquals(0L, SalaryCalculator.regularWorkedMs(s, d, at(2026, 11, 6, 8)))             // Before / 출근 전
        assertEquals(3 * 3_600_000L, SalaryCalculator.regularWorkedMs(s, d, at(2026, 11, 6, 12, 30))) // Lunch / 점심 중
        assertEquals(4 * 3_600_000L, SalaryCalculator.regularWorkedMs(s, d, at(2026, 11, 6, 14)))     // Afternoon / 오후
        assertEquals(8 * 3_600_000L, SalaryCalculator.regularWorkedMs(s, d, at(2026, 11, 6, 20)))     // After / 퇴근 후
        assertEquals(0L, SalaryCalculator.regularWorkedMs(s, LocalDate.of(2026, 11, 7), at(2026, 11, 7, 14))) // Saturday / 토요일
    }

    /** 주간 합계: 월~금 5일 × 하루치. */
    @Test fun weekTotal() {
        val day = SalaryCalculator.dailyWorkMs(s) / 1000.0 * SalaryCalculator.ratePerSecond(s, LocalDate.of(2026, 11, 6)) // Day pay / 하루치
        val sum = SalaryCalculator.regularEarnedSince(s, LocalDate.of(2026, 11, 2), at(2026, 11, 8, 12)) // Mon..Sun / 월~일
        assertEquals(day * 5, sum, 1e-6)
    }

    /** 연장수당: 일반 1.5배, 22~06시는 2.0배. */
    @Test fun overtime() {
        assertEquals(30_000.0, SalaryCalculator.overtimePay(at(2026, 11, 6, 19), at(2026, 11, 6, 21), 10_000.0), 1e-6) // 2h × 1.5
        assertEquals(35_000.0, SalaryCalculator.overtimePay(at(2026, 11, 6, 21), at(2026, 11, 6, 23), 10_000.0), 1e-6) // 1h×1.5 + 1h×2.0
        assertEquals(40_000.0, SalaryCalculator.overtimePay(at(2026, 11, 6, 23), at(2026, 11, 7, 1), 10_000.0), 1e-6)  // 2h × 2.0 (past midnight)
        assertEquals(50_000.0, SalaryCalculator.overtimePay(at(2026, 11, 7, 5), at(2026, 11, 7, 8), 10_000.0), 1e-6)   // 1h×2.0 + 2h×1.5
    }

    /** 통상시급 = 월급 ÷ 209. */
    @Test fun hourly() = assertEquals(4_200_000.0 / 209, SalaryCalculator.ordinaryHourly(s), 1e-9)

    /** 근무 상태 판단. */
    @Test fun status() {
        assertEquals(WorkStatus.BEFORE, SalaryCalculator.status(s, at(2026, 11, 6, 8, 59)))
        assertEquals(WorkStatus.WORKING, SalaryCalculator.status(s, at(2026, 11, 6, 9)))
        assertEquals(WorkStatus.LUNCH, SalaryCalculator.status(s, at(2026, 11, 6, 12, 10)))
        assertEquals(WorkStatus.AFTER, SalaryCalculator.status(s, at(2026, 11, 6, 18)))
        assertEquals(WorkStatus.OFF_DAY, SalaryCalculator.status(s, at(2026, 11, 8, 10)))
    }

    /** 연봉 입력은 ÷12. */
    @Test fun annual() = assertEquals(5_000_000.0, SalaryCalculator.monthlyPay(SalarySettings(payType = PayType.ANNUAL, payAmount = 60_000_000)), 1e-9)

    /** 주차·월 키. */
    @Test fun keys() {
        assertEquals("2026-W45", WorkCalendar.weekKey(LocalDate.of(2026, 11, 6)))
        assertEquals("2026-11", WorkCalendar.monthKey(LocalDate.of(2026, 11, 6)))
        assertEquals(LocalDate.of(2026, 11, 2), WorkCalendar.weekStart(LocalDate.of(2026, 11, 8)))
    }

    /** 시간 입력 파싱. */
    @Test fun parseTime() {
        assertEquals(540, Format.parseHm("09:00")); assertEquals(570, Format.parseHm("930"))
        assertEquals(1080, Format.parseHm("18")); assertEquals(null, Format.parseHm("25:00"))
    }
}
