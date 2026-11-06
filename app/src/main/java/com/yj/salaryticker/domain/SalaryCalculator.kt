package com.yj.salaryticker.domain

import com.yj.salaryticker.data.PayType
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.data.SessionState
import java.time.LocalDate

/**
 * Pure salary math (no Android dependency, easy to unit test).
 * 순수 계산 로직. Java의 static 유틸 클래스와 같은 object.
 */
object SalaryCalculator {

    /**
     * Monthly pay from annual or monthly input.
     * 입력이 연봉이면 ÷12, 월급이면 그대로.
     *
     * @param s 급여 설정
     * @return 월 급여(원)
     */
    fun monthlyPay(s: SalarySettings): Double = when (s.payType) {
        PayType.ANNUAL -> s.payAmount / 12.0   // Annual → monthly / 연봉 → 월급
        PayType.MONTHLY -> s.payAmount.toDouble() // Already monthly / 월급 그대로
    }

    /**
     * Won earned per second.
     * 초당 급여 = 월급 ÷ (이번 달 근무일 × 하루 근무시간 × 3600).
     *
     * @param s 급여 설정
     * @param today 기준 날짜 (근무일 자동 계산용)
     * @return 초당 급여(원), 설정이 비어 있으면 0
     */
    fun ratePerSecond(s: SalarySettings, today: LocalDate = LocalDate.now()): Double {
        val days = WorkCalendar.workDaysInMonth(today)          // Auto workdays / 자동 근무일
        val seconds = days * s.workHoursPerDay * 3600.0         // Monthly work seconds / 월 근무 초
        if (s.payAmount <= 0L || seconds <= 0.0) return 0.0     // Guard invalid input / 잘못된 입력 방어
        return monthlyPay(s) / seconds                          // Core formula / 핵심 공식
    }

    /**
     * Total worked millis today (stopped + current running segment).
     * 오늘 근무한 총 밀리초 (정지 누적 + 현재 실행 구간).
     *
     * @param session 세션 상태
     * @param nowMs 현재 epoch ms
     * @return 근무 밀리초
     */
    fun elapsedMs(session: SessionState, nowMs: Long): Long {
        val running = if (session.isRunning) (nowMs - session.runningSinceMs).coerceAtLeast(0L) else 0L // Current segment / 현재 구간
        return session.accumulatedMs + running                                                        // Sum / 합계
    }

    /**
     * Won earned today.
     * 오늘 번 금액.
     *
     * @param session 세션 상태
     * @param rate 초당 급여
     * @param nowMs 현재 epoch ms
     * @return 누적 금액(원)
     */
    fun earned(session: SessionState, rate: Double, nowMs: Long): Double =
        elapsedMs(session, nowMs) / 1000.0 * rate // millis → sec × rate / 초 × 초당급여

    /**
     * Expected full-day pay.
     * 하루 목표 금액 (진행률 계산용).
     *
     * @param s 급여 설정
     * @return 하루치 급여(원)
     */
    fun dailyTarget(s: SalarySettings): Double =
        ratePerSecond(s) * s.workHoursPerDay * 3600.0 // Rate × day seconds / 초당급여 × 하루 초
}
