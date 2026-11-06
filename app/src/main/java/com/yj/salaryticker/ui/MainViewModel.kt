package com.yj.salaryticker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yj.salaryticker.data.FakeLeaderboardRepository
import com.yj.salaryticker.data.LeaderboardRepository
import com.yj.salaryticker.data.RankEntry
import com.yj.salaryticker.data.SalaryPrefs
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.data.SessionState
import com.yj.salaryticker.domain.SalaryCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Everything the screens need to draw.
 * 화면이 그리는 데 필요한 상태 묶음.
 *
 * @property settings 급여 설정
 * @property session 세션 상태
 * @property ratePerSecond 초당 급여
 * @property earnedToday 오늘 누적액
 * @property elapsedMs 오늘 근무 밀리초
 * @property dailyTarget 하루 목표액
 * @property ranking 랭킹 목록(내 금액은 실시간 반영)
 */
data class UiState(
    val settings: SalarySettings = SalarySettings(), // Settings / 설정
    val session: SessionState = SessionState(),      // Session / 세션
    val ratePerSecond: Double = 0.0,                 // Won/sec / 초당 급여
    val earnedToday: Double = 0.0,                   // Today's earnings / 오늘 누적
    val elapsedMs: Long = 0L,                        // Worked millis / 근무 시간
    val dailyTarget: Double = 0.0,                   // Day target / 하루 목표
    val ranking: List<RankEntry> = emptyList(),      // Ranking / 랭킹
)

/**
 * Main ViewModel: ticker, start/stop, ranking sync.
 * 메인 뷰모델 (Spring의 Service 계층 느낌). 1초 티커로 화면 갱신.
 *
 * @param app 애플리케이션
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SalaryPrefs(app)                                       // Local storage / 로컬 저장소
    private val leaderboard: LeaderboardRepository = FakeLeaderboardRepository() // Swap to Firestore later / 나중에 Firestore로 교체
    private var lastSubmitMs = 0L                                              // Last upload time / 마지막 업로드 시각

    // Emits current time every TICK_MS / TICK_MS마다 현재 시각 방출
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis()) // Now / 현재 시각
            delay(TICK_MS)                   // Wait / 대기
        }
    }

    /**
     * Screen state combined from storage, ticker, and ranking.
     * 저장소 + 티커 + 랭킹을 합친 화면 상태.
     *
     * @return UiState StateFlow
     */
    val uiState: StateFlow<UiState> = combine(
        prefs.settingsFlow,            // Settings stream / 설정 스트림
        prefs.sessionFlow,             // Session stream / 세션 스트림
        ticker,                        // Clock / 시계
        leaderboard.observeRanking(),  // Ranking stream / 랭킹 스트림
    ) { settings, session, now, ranking ->
        val rate = SalaryCalculator.ratePerSecond(settings)       // Won/sec / 초당 급여
        val earned = SalaryCalculator.earned(session, rate, now)  // Earned / 누적액
        maybeSubmit(settings.nickname, earned, now)               // Throttled upload / 주기적 업로드
        UiState(
            settings = settings,
            session = session,
            ratePerSecond = rate,
            earnedToday = earned,
            elapsedMs = SalaryCalculator.elapsedMs(session, now),
            dailyTarget = SalaryCalculator.dailyTarget(settings),
            ranking = overlayMe(ranking, settings.nickname, earned), // Live "me" row / 내 줄 실시간 반영
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState()) // Keep 5s after rotate / 회전 시 5초 유지

    init {
        viewModelScope.launch { resetIfNewDay() } // Daily reset on launch / 앱 시작 시 날짜 체크
    }

    /**
     * Start counting.
     * 카운터 시작. 시작 시각만 저장하고 금액은 매번 계산.
     */
    fun start() = viewModelScope.launch {
        val s = resetIfNewDay()                                                  // Ensure today / 오늘 세션 보장
        if (s.isRunning) return@launch                                           // Already running / 이미 실행 중
        prefs.saveSession(s.copy(runningSinceMs = System.currentTimeMillis()))   // Save start time / 시작시각 저장
    }

    /**
     * Pause counting (keeps accumulated amount).
     * 일시정지. 지금까지 구간을 누적에 합침.
     */
    fun stop() = viewModelScope.launch {
        val s = prefs.sessionFlow.first()                                        // Current session / 현재 세션
        if (!s.isRunning) return@launch                                          // Not running / 실행 중 아님
        val acc = SalaryCalculator.elapsedMs(s, System.currentTimeMillis())      // Fold segment / 구간 합산
        prefs.saveSession(s.copy(accumulatedMs = acc, runningSinceMs = 0L))      // Save stopped / 정지 상태 저장
    }

    /**
     * Reset today's counter to zero.
     * 오늘 카운터 0으로 초기화.
     */
    fun reset() = viewModelScope.launch {
        prefs.saveSession(SessionState(date = today())) // Fresh session / 새 세션
    }

    /**
     * Save settings from the settings screen.
     * 설정 화면 값 저장.
     *
     * @param s 새 설정
     */
    fun saveSettings(s: SalarySettings) = viewModelScope.launch {
        prefs.saveSettings(s) // Persist / 저장
    }

    /**
     * Reset session if date changed (ranking is "today" based).
     * 날짜가 바뀌었으면 세션 초기화 (랭킹이 '오늘 누적' 기준이라).
     *
     * @return 오늘 기준 세션
     */
    private suspend fun resetIfNewDay(): SessionState {
        val s = prefs.sessionFlow.first()               // Read current / 현재 값
        if (s.date == today()) return s                 // Same day / 같은 날이면 그대로
        val fresh = SessionState(date = today())        // New day / 새 날짜
        prefs.saveSession(fresh)                        // Persist / 저장
        return fresh                                    // Return fresh / 새 세션 반환
    }

    /**
     * Upload my score at most every SUBMIT_INTERVAL_MS (saves server cost).
     * 서버 비용 절감을 위해 일정 간격으로만 업로드.
     *
     * @param nickname 닉네임
     * @param amount 누적액
     * @param now 현재 시각
     */
    private fun maybeSubmit(nickname: String, amount: Double, now: Long) {
        if (now - lastSubmitMs < SUBMIT_INTERVAL_MS) return             // Too soon / 아직 이름
        lastSubmitMs = now                                              // Mark time / 시각 기록
        viewModelScope.launch { leaderboard.submitMyScore(nickname, amount) } // Upload / 업로드
    }

    /**
     * Replace my (possibly stale) server row with the live value and re-sort.
     * 서버의 내 점수(지연값)를 실시간 값으로 덮어쓰고 재정렬.
     *
     * @param ranking 서버 랭킹
     * @param nickname 내 닉네임
     * @param earned 내 실시간 누적액
     * @return 보정된 랭킹
     */
    private fun overlayMe(ranking: List<RankEntry>, nickname: String, earned: Double): List<RankEntry> {
        val others = ranking.filterNot { it.isMe }                                    // Remove stale me / 지연된 내 줄 제거
        val me = RankEntry(FakeLeaderboardRepository.ME_ID, nickname, earned, isMe = true) // Live me / 실시간 내 줄
        return (others + me).sortedByDescending { it.amount }                         // Re-sort / 재정렬
    }

    /** @return 오늘 날짜 문자열(yyyy-MM-dd) */
    private fun today(): String = LocalDate.now().toString() // ISO date / ISO 날짜

    private companion object {
        const val TICK_MS = 1_000L              // Screen refresh period (lower = smoother) / 화면 갱신 주기
        const val SUBMIT_INTERVAL_MS = 10_000L  // Ranking upload period / 랭킹 업로드 주기
    }
}
