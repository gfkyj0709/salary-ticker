package com.yj.salaryticker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yj.salaryticker.data.FirestoreLeaderboardRepository
import com.yj.salaryticker.data.LeaderboardRepository
import com.yj.salaryticker.data.RankEntry
import com.yj.salaryticker.data.SalaryPrefs
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.data.SessionState
import com.yj.salaryticker.domain.SalaryCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val leaderboard: LeaderboardRepository = FirestoreLeaderboardRepository() // Real ranking server / 실제 랭킹 서버

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
        UiState(
            settings = settings,
            session = session,
            ratePerSecond = rate,
            earnedToday = earned,
            elapsedMs = SalaryCalculator.elapsedMs(session, now),
            dailyTarget = SalaryCalculator.dailyTarget(settings),
            ranking = buildRanking(ranking, settings.nickname, rate, session, now), // Live amounts / 실시간 금액
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState()) // Keep 5s after rotate / 회전 시 5초 유지

    init {
        viewModelScope.launch { resetIfNewDay() } // Daily reset on launch / 앱 시작 시 날짜 체크
        syncMyStateToServer()                     // Upload on change only / 변경 시에만 업로드
    }

    /**
     * Upload my state only when it actually changes (start/stop/reset/settings).
     * 상태가 바뀔 때만 업로드 → 1인당 하루 쓰기 몇 번 수준 (무료 한도 절약).
     */
    @OptIn(FlowPreview::class)
    private fun syncMyStateToServer() = viewModelScope.launch {
        combine(prefs.settingsFlow, prefs.sessionFlow) { st, se ->
            Triple(st.nickname, SalaryCalculator.ratePerSecond(st), se)       // Upload payload / 업로드 내용
        }
            .distinctUntilChanged()                                           // Skip duplicates / 같은 값 무시
            .debounce(1_500L)                                                 // Merge rapid taps / 연타 합치기
            .collect { (nick, rate, se) ->
                leaderboard.submitMyState(nick, rate, se.accMs, se.runningSinceMs) // Upload / 업로드
            }
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
     * Compute everyone's live amount, replace my row with local (freshest) state, sort.
     * 모든 참가자 금액을 매초 계산, 내 줄은 로컬 최신값으로 교체 후 정렬.
     *
     * @param server 서버에서 받은 참가자 목록
     * @param nickname 내 닉네임
     * @param rate 내 초당 급여
     * @param session 내 세션
     * @param now 현재 시각
     * @return 금액 내림차순 랭킹
     */
    private fun buildRanking(
        server: List<RankEntry>, nickname: String, rate: Double, session: SessionState, now: Long,
    ): List<RankEntry> {
        val myId = leaderboard.myUserId() ?: "me"                                     // My ID / 내 ID
        val others = server.filterNot { it.isMe || it.userId == myId }                 // Drop server copy of me / 서버의 내 줄 제거
            .map { it.copy(amount = it.amountAt(now)) }                                // Live amount / 실시간 금액
            .filter { it.amount > 0.0 }                                                // Hide zero rows / 0원 숨김
        val me = RankEntry(myId, nickname, rate, session.accumulatedMs, session.runningSinceMs, isMe = true)
            .let { it.copy(amount = it.amountAt(now)) }                                // My live row / 내 실시간 줄
        return (others + me).sortedByDescending { it.amount }                          // Sort desc / 내림차순
    }

    /** @return 오늘 날짜 문자열(yyyy-MM-dd) */
    private fun today(): String = LocalDate.now().toString() // ISO date / ISO 날짜

    private companion object {
        const val TICK_MS = 1_000L              // Screen refresh period (lower = smoother) / 화면 갱신 주기
    }
}
