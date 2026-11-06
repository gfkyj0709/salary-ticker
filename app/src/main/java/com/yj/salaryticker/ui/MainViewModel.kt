package com.yj.salaryticker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yj.salaryticker.data.DayOvertime
import com.yj.salaryticker.data.FirestoreLeaderboardRepository
import com.yj.salaryticker.data.LeaderboardRepository
import com.yj.salaryticker.data.LupinState
import com.yj.salaryticker.data.OvertimeState
import com.yj.salaryticker.data.RemoteUser
import com.yj.salaryticker.data.SalaryPrefs
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.domain.SalaryCalculator
import com.yj.salaryticker.domain.WorkCalendar
import com.yj.salaryticker.domain.WorkStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 랭킹 범위 / Ranking scope */
enum class RankScope { ALL, GROUP }       // 전체 / 그룹

/** 랭킹 기간 / Ranking period */
enum class RankPeriod { TODAY, WEEK, MONTH } // 오늘 / 이번 주 / 이번 달

/** 랭킹 종류 / Ranking kind */
enum class RankKind { PAY, OVERTIME_LOSS, LUPIN }  // 월급 레이스 / 야근 손해 / 월급루팡

/**
 * Ranking tab selection.
 * 랭킹 탭에서 고른 조건.
 *
 * @property scope 범위
 * @property period 기간
 * @property kind 종류
 */
data class RankSelection(
    val scope: RankScope = RankScope.ALL,       // Default all / 기본 전체
    val period: RankPeriod = RankPeriod.TODAY,  // Default today / 기본 오늘
    val kind: RankKind = RankKind.PAY,          // Default pay / 기본 월급
)

/**
 * One ranking row for display.
 * 화면 표시용 랭킹 한 줄.
 *
 * @property id 사용자 ID
 * @property nickname 닉네임
 * @property amount 금액
 * @property isMe 내 항목 여부
 * @property active 지금 근무/야근 중인지
 * @property slacking 지금 루팡 중인지
 */
data class RankRow(
    val id: String, val nickname: String, val amount: Double, val isMe: Boolean, val active: Boolean,
    val slacking: Boolean = false,                                  // 지금 루팡 중 / Slacking now
)

/**
 * Everything the screens need.
 * 화면 상태 묶음.
 */
data class UiState(
    val settings: SalarySettings = SalarySettings(),  // Settings / 설정
    val status: WorkStatus = WorkStatus.BEFORE,       // Work status / 근무 상태
    val ratePerSecond: Double = 0.0,                  // Won/sec / 초당 급여
    val earnedToday: Double = 0.0,                    // Today's regular pay / 오늘 정규 수입
    val workedMs: Long = 0L,                          // Today's regular ms / 오늘 정규 근무
    val dailyTarget: Double = 0.0,                    // Day target / 하루치
    val msUntilClockOut: Long = 0L,                   // Until clock-out / 퇴근까지
    val overtime: OvertimeState = OvertimeState(),    // OT state / 야근 상태
    val otTodayMs: Long = 0L,                         // OT time today / 오늘 야근 시간
    val otTodayPay: Double = 0.0,                     // OT unpaid today / 오늘 못 받은 수당
    val otMonthPay: Double = 0.0,                     // OT unpaid this month / 이번 달 못 받은 수당
    val hourly: Double = 0.0,                         // Ordinary hourly / 통상시급
    val lupin: LupinState = LupinState(),             // Slacking state / 루팡 상태
    val lpTodayMs: Long = 0L,                         // Slacking time today / 오늘 루팡 시간
    val lpTodayPay: Double = 0.0,                     // Paid while slacking today / 오늘 루팡 금액
    val lpMonthPay: Double = 0.0,                     // This month / 이번 달 루팡 금액
    val selection: RankSelection = RankSelection(),   // Ranking filter / 랭킹 조건
    val ranking: List<RankRow> = emptyList(),         // Ranking / 랭킹
)

/**
 * All local data in one bundle.
 * 로컬 데이터 묶음 (설정 + 야근 + 루팡).
 */
data class LocalData(
    val s: SalarySettings,                    // Settings / 설정
    val ot: OvertimeState,                    // OT today / 오늘 야근
    val otHist: Map<String, DayOvertime>,     // OT history / 야근 기록
    val lp: LupinState,                       // Lupin today / 오늘 루팡
    val lpHist: Map<String, DayOvertime>,     // Lupin history / 루팡 기록
)

/**
 * Main ViewModel (Service layer role). Regular pay is schedule-based, overtime is button-based.
 * 메인 뷰모델 (Spring Service 계층 느낌). 정규 근무는 시간표로 자동, 야근은 버튼으로 기록.
 *
 * @param app 애플리케이션
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SalaryPrefs(app)                                                // Local storage / 로컬 저장소
    private val leaderboard: LeaderboardRepository = FirestoreLeaderboardRepository()   // Ranking server / 랭킹 서버
    private val selection = MutableStateFlow(RankSelection())                           // Ranking filter / 랭킹 조건

    // Emits current time every second / 1초마다 현재 시각 방출
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(TICK_MS) } }

    // Local data bundle / 로컬 데이터 묶음
    private val local = combine(
        prefs.settingsFlow, prefs.overtimeFlow, prefs.historyFlow, prefs.lupinFlow, prefs.lupinHistoryFlow,
    ) { s, o, oh, l, lh -> LocalData(s, o, oh, l, lh) }

    /** @return 화면 상태 StateFlow */
    val uiState: StateFlow<UiState> = combine(
        local, ticker, leaderboard.observeAll().onStart { emit(emptyList()) }, selection, // Inputs / 입력
    ) { l, now, server, sel ->
        val s = l.s; val ot = l.ot                                                      // Shortcuts / 단축
        val today = LocalDate.now(SalaryCalculator.zone)                                // Today / 오늘
        val me = buildMe(l, today)                                                      // My server-shaped row / 내 서버 형태
        val lpToday = lupinTodayPay(me, today, now)                                     // Lupin today / 오늘 루팡
        val otToday = otTodayPay(me, today, now)                                        // OT unpaid today / 오늘 손해
        UiState(
            settings = s,
            status = SalaryCalculator.status(s, now),
            ratePerSecond = SalaryCalculator.ratePerSecond(s, today),
            earnedToday = SalaryCalculator.regularEarnedOn(s, today, now),
            workedMs = SalaryCalculator.regularWorkedMs(s, today, now),
            dailyTarget = SalaryCalculator.dailyWorkMs(s) / 1000.0 * SalaryCalculator.ratePerSecond(s, today),
            msUntilClockOut = SalaryCalculator.msUntilClockOut(s, now),
            overtime = ot,
            otTodayMs = if (ot.date == today.toString()) ot.accMs + (if (ot.isRunning) now - ot.sinceMs else 0L) else 0L,
            otTodayPay = otToday,
            otMonthPay = me.otMonthBase + otToday,
            hourly = me.hourly,
            lupin = l.lp,
            lpTodayMs = if (l.lp.date == today.toString()) l.lp.accMs + (if (l.lp.isRunning) SalaryCalculator.paidMsBetween(s, l.lp.sinceMs, now) else 0L) else 0L,
            lpTodayPay = lpToday,
            lpMonthPay = me.lpMonthBase + lpToday,
            selection = sel,
            ranking = buildRanking(server, me, sel, today, now),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState()) // Keep 5s after rotate / 회전 시 5초 유지

    init {
        viewModelScope.launch { while (true) { rollDayIfNeeded(); delay(30_000L) } } // Midnight check every 30s / 30초마다 날짜 체크
        syncToServer()                                                              // Upload on change / 변경 시 업로드
    }

    // ---------- Actions / 사용자 동작 ----------

    /**
     * Start or stop overtime.
     * 야근 시작/종료 토글. 종료 시 그 구간의 법정 수당을 누적.
     */
    fun toggleOvertime() = viewModelScope.launch {
        val ot = rollDayIfNeeded()                                                   // Ensure today / 오늘 기준 보장
        val s = prefs.settingsFlow.first()                                           // Settings / 설정
        val now = System.currentTimeMillis()                                         // Now / 현재
        val next = if (ot.isRunning) {
            val end = minOf(now, ot.sinceMs + RemoteUser.MAX_OT_MS)                  // Cap 16h / 16시간 제한
            ot.copy(
                accMs = ot.accMs + (end - ot.sinceMs),                               // Add time / 시간 누적
                accPay = ot.accPay + SalaryCalculator.overtimePay(ot.sinceMs, end, SalaryCalculator.ordinaryHourly(s)), // Add pay / 수당 누적
                sinceMs = 0L,                                                        // Stop / 정지
            )
        } else ot.copy(sinceMs = now)                                                // Start / 시작
        prefs.saveOvertime(next)                                                     // Persist / 저장
    }

    /**
     * Clear today's overtime record.
     * 오늘 야근 기록 초기화.
     */
    fun resetOvertimeToday() = viewModelScope.launch {
        prefs.saveOvertime(OvertimeState(date = LocalDate.now(SalaryCalculator.zone).toString())) // Fresh / 새 기록
    }

    /**
     * Start or stop slacking (only during paid work hours).
     * 루팡 시작/종료. 종료 시 그 구간 중 '돈 받는 시간'만큼 금액 누적.
     */
    fun toggleLupin() = viewModelScope.launch {
        rollDayIfNeeded()                                                            // Ensure today / 오늘 기준
        val lp = prefs.lupinFlow.first()                                             // Current / 현재
        val s = prefs.settingsFlow.first()                                           // Settings / 설정
        val now = System.currentTimeMillis()                                         // Now / 현재 시각
        val next = if (lp.isRunning) lp.copy(
            accMs = lp.accMs + SalaryCalculator.paidMsBetween(s, lp.sinceMs, now),   // Paid time only / 유급시간만
            accPay = lp.accPay + SalaryCalculator.slackPay(s, lp.sinceMs, now),      // Add pay / 금액 누적
            sinceMs = 0L,                                                            // Stop / 정지
        ) else if (SalaryCalculator.status(s, now) == WorkStatus.WORKING) lp.copy(sinceMs = now) // Start in work hours / 근무 중에만 시작
        else return@launch                                                           // Not working → ignore / 근무 외 무시
        prefs.saveLupin(next)                                                        // Persist / 저장
    }

    /**
     * Clear today's slacking record.
     * 오늘 루팡 기록 초기화.
     */
    fun resetLupinToday() = viewModelScope.launch {
        prefs.saveLupin(LupinState(date = LocalDate.now(SalaryCalculator.zone).toString())) // Fresh / 새 기록
    }

    /**
     * Save settings.
     * 설정 저장.
     *
     * @param s 새 설정
     */
    fun saveSettings(s: SalarySettings) = viewModelScope.launch { prefs.saveSettings(s) } // Persist / 저장

    /**
     * Change ranking filter.
     * 랭킹 조건 변경.
     *
     * @param f 변경 함수
     */
    fun select(f: (RankSelection) -> RankSelection) = selection.update(f) // Update / 갱신

    /**
     * New random group code (no confusing chars like 0/O, 1/I).
     * 새 그룹 코드 생성 (헷갈리는 0/O, 1/I 제외).
     *
     * @return 6자리 코드
     */
    fun newGroupCode(): String = (1..6).map { CODE_CHARS.random() }.joinToString("") // Random 6 / 랜덤 6자리

    // ---------- Internals / 내부 로직 ----------

    /**
     * On a new day: move yesterday's OT into history; OT running past midnight continues today.
     * 날짜가 바뀌면 어제 야근을 기록으로 옮김. 자정을 넘긴 야근은 오늘로 이어짐.
     *
     * @return 오늘 기준 야근 상태
     */
    private suspend fun rollDayIfNeeded(): OvertimeState {
        rollLupinIfNeeded()                                                        // Lupin too / 루팡도 정리
        val ot = prefs.overtimeFlow.first()                                        // Current / 현재
        val today = LocalDate.now(SalaryCalculator.zone)                           // Today / 오늘
        if (ot.date == today.toString()) return ot                                 // Same day / 같은 날
        if (ot.date.isEmpty()) {                                                   // First run / 최초 실행
            return OvertimeState(date = today.toString()).also { prefs.saveOvertime(it) }
        }
        val s = prefs.settingsFlow.first()                                         // Settings / 설정
        val midnight = today.atStartOfDay(SalaryCalculator.zone).toInstant().toEpochMilli() // Today 00:00 / 오늘 0시
        val segEnd = if (ot.isRunning) minOf(midnight, ot.sinceMs + RemoteUser.MAX_OT_MS) else 0L // Close at midnight / 자정에 마감
        val oldMs = ot.accMs + if (ot.isRunning) (segEnd - ot.sinceMs).coerceAtLeast(0L) else 0L   // Old day time / 어제 시간
        val oldPay = ot.accPay + if (ot.isRunning) SalaryCalculator.overtimePay(ot.sinceMs, segEnd, SalaryCalculator.ordinaryHourly(s)) else 0.0 // Old day pay / 어제 수당
        val history = prefs.historyFlow.first().toMutableMap()                     // History / 기록
        if (oldMs > 0) history[ot.date] = DayOvertime(oldMs, oldPay)               // Archive / 보관
        val carry = ot.isRunning && ot.date == today.minusDays(1).toString() && ot.sinceMs + RemoteUser.MAX_OT_MS > midnight // Continue? / 이어갈지
        val fresh = OvertimeState(date = today.toString(), sinceMs = if (carry) midnight else 0L) // New day / 새 날
        prefs.saveOvertime(fresh, history)                                         // Persist / 저장
        return fresh
    }

    /**
     * On a new day: archive yesterday's slacking (work hours never cross midnight, so just close it).
     * 날짜가 바뀌면 어제 루팡을 기록으로 옮김 (근무시간은 자정을 안 넘으니 그냥 마감).
     */
    private suspend fun rollLupinIfNeeded() {
        val lp = prefs.lupinFlow.first()                                           // Current / 현재
        val today = LocalDate.now(SalaryCalculator.zone).toString()                // Today / 오늘
        if (lp.date == today) return                                               // Same day / 같은 날
        val history = prefs.lupinHistoryFlow.first().toMutableMap()                // History / 기록
        if (lp.date.isNotEmpty()) {
            val s = prefs.settingsFlow.first()                                     // Settings / 설정
            val now = System.currentTimeMillis()                                   // Now / 현재
            val ms = lp.accMs + if (lp.isRunning) SalaryCalculator.paidMsBetween(s, lp.sinceMs, now) else 0L // Close / 마감
            val pay = lp.accPay + if (lp.isRunning) SalaryCalculator.slackPay(s, lp.sinceMs, now) else 0.0
            if (ms > 0) history[lp.date] = DayOvertime(ms, pay)                    // Archive / 보관
        }
        prefs.saveLupin(LupinState(date = today), history)                         // New day / 새 날
    }

    /**
     * Build my server row from local data (also used for my ranking line).
     * 로컬 데이터로 내 서버용 데이터 생성 (내 랭킹 줄에도 사용).
     *
     * @return 내 정보
     */
    private fun buildMe(l: LocalData, today: LocalDate): RemoteUser {
        val s = l.s; val ot = l.ot; val hist = l.otHist                                   // Shortcuts / 단축
        val t = today.toString()                                                          // Today key / 오늘 키
        val ws = WorkCalendar.weekStart(today).toString()                                 // Monday / 월요일
        val mk = WorkCalendar.monthKey(today)                                             // Month key / 월 키
        return RemoteUser(
            uid = leaderboard.myUserId() ?: "me",
            nickname = s.nickname, group = s.group,
            monthly = SalaryCalculator.monthlyPay(s),
            startMin = s.startMin, endMin = s.endMin, lunchStartMin = s.lunchStartMin, lunchMin = s.lunchMin,
            hourly = SalaryCalculator.ordinaryHourly(s),
            otDate = ot.date, otAccMs = ot.accMs, otAccPay = ot.accPay, otSince = ot.sinceMs,
            weekKey = WorkCalendar.weekKey(today),
            otWeekBase = hist.filterKeys { it >= ws && it < t }.values.sumOf { it.pay },      // Past days this week / 이번 주 지난 날
            monthKey = mk,
            otMonthBase = hist.filterKeys { it.startsWith(mk) && it < t }.values.sumOf { it.pay }, // Past days this month / 이번 달 지난 날
            lastDay = t,
            lpDate = l.lp.date, lpAccMs = l.lp.accMs, lpAccPay = l.lp.accPay, lpSince = l.lp.sinceMs,
            lpWeekBase = l.lpHist.filterKeys { it >= ws && it < t }.values.sumOf { it.pay },     // Lupin week / 루팡 주간
            lpMonthBase = l.lpHist.filterKeys { it.startsWith(mk) && it < t }.values.sumOf { it.pay }, // Lupin month / 루팡 월간
        )
    }

    /**
     * Live slacking pay today, capped by today's regular pay (anti-cheat).
     * 오늘 루팡 금액 (실시간). 오늘 정규 수입을 넘을 수 없게 제한 (조작 방지).
     *
     * @return 금액
     */
    private fun lupinTodayPay(u: RemoteUser, today: LocalDate, now: Long): Double {
        if (u.lpDate != today.toString()) return 0.0                                         // Stale / 오늘 기록 아님
        val s = u.toSettings()                                                               // Settings / 설정
        val live = if (u.lpSince > 0) SalaryCalculator.slackPay(s, u.lpSince, now) else 0.0  // Running part / 진행분
        return (u.lpAccPay + live).coerceAtMost(SalaryCalculator.regularEarnedOn(s, today, now)) // Cap / 상한
    }

    /**
     * Live unpaid OT today for any user.
     * 오늘 야근 손해 (실시간).
     *
     * @return 금액
     */
    private fun otTodayPay(u: RemoteUser, today: LocalDate, now: Long): Double {
        if (u.otDate != today.toString()) return 0.0                                         // Stale / 오늘 기록 아님
        val live = if (u.otSince > 0) SalaryCalculator.overtimePay(u.otSince, minOf(now, u.otSince + RemoteUser.MAX_OT_MS), u.hourly) else 0.0 // Running part / 진행분
        return u.otAccPay + live                                                             // Total / 합계
    }

    /**
     * Amount for one user under the selected filter.
     * 선택한 조건에서 한 사람의 금액.
     *
     * @return 금액
     */
    private fun amountOf(u: RemoteUser, sel: RankSelection, today: LocalDate, now: Long): Double {
        val s = u.toSettings()                                                                // Same calculator / 같은 계산기
        return when (sel.kind) {
            RankKind.PAY -> when (sel.period) {
                RankPeriod.TODAY -> SalaryCalculator.regularEarnedOn(s, today, now)            // Today / 오늘
                RankPeriod.WEEK -> SalaryCalculator.regularEarnedSince(s, WorkCalendar.weekStart(today), now) // Week / 주
                RankPeriod.MONTH -> SalaryCalculator.regularEarnedSince(s, today.withDayOfMonth(1), now)      // Month / 월
            }
            RankKind.LUPIN -> {
                val t = lupinTodayPay(u, today, now)                                          // Today lupin / 오늘 루팡
                val total = when (sel.period) {
                    RankPeriod.TODAY -> t
                    RankPeriod.WEEK -> t + if (u.weekKey == WorkCalendar.weekKey(today)) u.lpWeekBase else 0.0   // + week / 주간
                    RankPeriod.MONTH -> t + if (u.monthKey == WorkCalendar.monthKey(today)) u.lpMonthBase else 0.0 // + month / 월간
                }
                total.coerceAtMost(amountOf(u, sel.copy(kind = RankKind.PAY), today, now))     // ≤ regular pay / 정규 수입 이하
            }
            RankKind.OVERTIME_LOSS -> {
                val t = otTodayPay(u, today, now)                                             // Today OT / 오늘 야근
                when (sel.period) {
                    RankPeriod.TODAY -> t
                    RankPeriod.WEEK -> t + if (u.weekKey == WorkCalendar.weekKey(today)) u.otWeekBase else 0.0   // + past week / 주간 누적
                    RankPeriod.MONTH -> t + if (u.monthKey == WorkCalendar.monthKey(today)) u.otMonthBase else 0.0 // + past month / 월간 누적
                }
            }
        }
    }

    /**
     * Ranking: server users (recently active) + my live row, filtered and sorted.
     * 랭킹: 최근 7일 접속자 + 내 실시간 줄, 조건 필터 후 정렬.
     *
     * @return 랭킹 목록
     */
    private fun buildRanking(server: List<RemoteUser>, me: RemoteUser, sel: RankSelection, today: LocalDate, now: Long): List<RankRow> {
        val cutoff = today.minusDays(7).toString()                                            // Active within 7 days / 7일 이내
        val others = server.filter { it.uid != me.uid && it.lastDay >= cutoff }               // Drop me & inactive / 나·비활성 제외
        val pool = (others + me).filter {
            sel.scope == RankScope.ALL || (me.group.isNotEmpty() && it.group == me.group)     // Group filter / 그룹 필터
        }.filter { it.monthly > 0 }                                                           // Configured only / 설정 완료자만
        return pool.map { u ->
            val st = SalaryCalculator.status(u.toSettings(), now)                             // Status / 상태
            val otOn = u.otSince > 0 && u.otDate == today.toString()                          // OT running / 야근 중
            val lpOn = u.lpSince > 0 && u.lpDate == today.toString() && st == WorkStatus.WORKING // Slacking now / 루팡 중
            RankRow(u.uid, u.nickname, amountOf(u, sel, today, now), u === me, st == WorkStatus.WORKING || otOn, lpOn)
        }.filter { sel.kind == RankKind.PAY || it.amount > 0.0 || it.isMe }                   // Hide zero OT/lupin / 0원 숨김
            .sortedByDescending { it.amount }                                                 // Sort desc / 내림차순
    }

    /**
     * Upload my state only when it changes (settings, OT start/stop, new day).
     * 상태가 바뀔 때만 업로드 → 1인당 하루 쓰기 몇 번 수준.
     */
    @OptIn(FlowPreview::class)
    private fun syncToServer() = viewModelScope.launch {
        local.distinctUntilChanged()                                                          // Local data, skip duplicates / 로컬 데이터, 중복 무시
            .debounce(1_500L)                                                                 // Merge rapid taps / 연타 합치기
            .collect { l ->
                if (l.s.payAmount <= 0L) return@collect                                       // Not configured / 미설정
                val today = LocalDate.now(SalaryCalculator.zone)                              // Today / 오늘
                if (l.ot.date != today.toString()) return@collect                             // Wait for day roll / 날짜 정리 대기
                leaderboard.submit(buildMe(l, today))                                         // Upload / 업로드
            }
    }

    private companion object {
        const val TICK_MS = 1_000L                                  // Screen refresh / 화면 갱신 주기
        const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // Group code chars / 그룹 코드 문자
    }
}
