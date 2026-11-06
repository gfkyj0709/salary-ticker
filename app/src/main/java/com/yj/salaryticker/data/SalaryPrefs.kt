package com.yj.salaryticker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance per process / 프로세스당 DataStore 1개 (싱글톤)
private val Context.dataStore by preferencesDataStore(name = "salary_prefs")

/**
 * Input pay type.
 * 입력 급여 종류 (Java enum과 동일).
 */
enum class PayType { ANNUAL, MONTHLY } // 연봉 / 월급

/**
 * User salary settings.
 * 사용자 급여 설정 (Java의 DTO/VO 같은 역할). 근무일은 WorkCalendar가 자동 계산.
 *
 * @property nickname 랭킹에 표시될 닉네임
 * @property payType 연봉/월급 구분
 * @property payAmount 입력 금액(원)
 * @property workHoursPerDay 하루 실근무시간(휴게 제외)
 */
data class SalarySettings(
    val nickname: String = "나",               // Default nickname / 기본 닉네임
    val payType: PayType = PayType.ANNUAL,     // Default annual / 기본 연봉
    val payAmount: Long = 0L,                  // 0 means not set yet / 0이면 미설정
    val workHoursPerDay: Double = 8.0,         // Hours per day / 하루 근무시간
)

/**
 * Today's ticker session state.
 * 오늘의 카운터 세션 상태. 실행 중 여부는 시작 시각으로 판단.
 *
 * @property date 세션 날짜(yyyy-MM-dd), 날짜가 바뀌면 리셋
 * @property accumulatedMs 정지 전까지 누적된 근무 밀리초
 * @property runningSinceMs 실행 시작 epoch ms, 0이면 정지 상태
 */
data class SessionState(
    val date: String = "",          // Session date / 세션 날짜
    val accumulatedMs: Long = 0L,   // Stopped segments total / 정지 구간 합계
    val runningSinceMs: Long = 0L,  // 0 = stopped / 0이면 정지
) {
    /** @return 현재 카운터가 돌고 있으면 true */
    val isRunning: Boolean get() = runningSinceMs > 0L // Running check / 실행 여부
}

/**
 * DataStore wrapper for settings and session.
 * 설정과 세션을 저장하는 저장소 클래스 (Java의 DAO 역할).
 * 시작 "시각"을 저장하므로 앱이 꺼져도 금액이 계속 쌓인 것으로 계산됨.
 *
 * @param context 애플리케이션 컨텍스트
 */
class SalaryPrefs(private val context: Context) {

    // Preference keys / 저장 키 모음 (Java의 static final 상수)
    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")          // Nickname key / 닉네임 키
        val OLD_MONTHLY = longPreferencesKey("monthly_salary")   // v0.1 key, migration only / 구버전 월급 키 (이전용)
        val PAY_TYPE = stringPreferencesKey("pay_type")          // Pay type key / 급여종류 키
        val PAY_AMOUNT = longPreferencesKey("pay_amount")        // Amount key / 금액 키
        val HOURS = doublePreferencesKey("work_hours")           // Hours key / 근무시간 키
        val S_DATE = stringPreferencesKey("session_date")        // Session date key / 세션 날짜 키
        val S_ACC = longPreferencesKey("session_acc_ms")         // Accumulated key / 누적 키
        val S_RUN = longPreferencesKey("session_running_since")  // Running-since key / 시작시각 키
    }

    /**
     * Settings stream.
     * 설정 변경을 실시간으로 흘려주는 Flow.
     *
     * @return 설정 Flow
     */
    val settingsFlow: Flow<SalarySettings> = context.dataStore.data.map { p: Preferences ->
        val old = p[OLD_MONTHLY]                                     // v0.1 monthly value / 구버전 월급값
        val migrate = p[PAY_AMOUNT] == null && old != null           // Needs migration? / 이전 필요?
        SalarySettings(
            nickname = p[NICKNAME] ?: "나",                          // Fallback nickname / 기본값
            payType = if (migrate) PayType.MONTHLY                   // Old data was monthly / 구버전은 월급
                      else p[PAY_TYPE]?.let(PayType::valueOf) ?: PayType.ANNUAL, // Saved or default / 저장값 또는 기본
            payAmount = p[PAY_AMOUNT] ?: old ?: 0L,                  // New, old, or 0 / 신규→구버전→0
            workHoursPerDay = p[HOURS] ?: 8.0,                       // Fallback hours / 기본값
        )
    }

    /**
     * Session stream.
     * 세션 상태 Flow.
     *
     * @return 세션 Flow
     */
    val sessionFlow: Flow<SessionState> = context.dataStore.data.map { p ->
        SessionState(
            date = p[S_DATE] ?: "",          // Empty = never started / 빈값이면 최초
            accumulatedMs = p[S_ACC] ?: 0L,  // Accumulated / 누적
            runningSinceMs = p[S_RUN] ?: 0L, // Running since / 시작시각
        )
    }

    /**
     * Save settings.
     * 설정 저장.
     *
     * @param s 저장할 설정
     */
    suspend fun saveSettings(s: SalarySettings) {
        context.dataStore.edit { p ->
            p[NICKNAME] = s.nickname          // Save nickname / 닉네임 저장
            p[PAY_TYPE] = s.payType.name      // Save type / 급여종류 저장
            p[PAY_AMOUNT] = s.payAmount       // Save amount / 금액 저장
            p[HOURS] = s.workHoursPerDay      // Save hours / 근무시간 저장
        }
    }

    /**
     * Save session.
     * 세션 저장.
     *
     * @param s 저장할 세션
     */
    suspend fun saveSession(s: SessionState) {
        context.dataStore.edit { p ->
            p[S_DATE] = s.date              // Save date / 날짜 저장
            p[S_ACC] = s.accumulatedMs      // Save accumulated / 누적 저장
            p[S_RUN] = s.runningSinceMs     // Save running-since / 시작시각 저장
        }
    }
}
