package com.yj.salaryticker.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// Single DataStore instance per process / 프로세스당 DataStore 1개 (싱글톤)
private val Context.dataStore by preferencesDataStore(name = "salary_prefs")

/**
 * Input pay type.
 * 입력 급여 종류 (Java enum과 동일).
 */
enum class PayType { ANNUAL, MONTHLY } // 연봉 / 월급

/**
 * User salary settings (DTO). Work time comes from the schedule, workdays from the calendar.
 * 사용자 설정 (Java DTO). 근무시간은 출퇴근 시간표, 근무일은 달력에서 자동 계산.
 *
 * @property nickname 랭킹 닉네임
 * @property payType 연봉/월급 구분
 * @property payAmount 입력 금액(원)
 * @property startMin 출근 시각 (0시부터 분)
 * @property endMin 퇴근 시각 (분)
 * @property lunchStartMin 점심 시작 (분)
 * @property lunchMin 점심 길이 (분)
 * @property group 친구 그룹 코드 (없으면 빈 문자열)
 */
data class SalarySettings(
    val nickname: String = "나",            // Default nickname / 기본 닉네임
    val payType: PayType = PayType.ANNUAL,  // Default annual / 기본 연봉
    val payAmount: Long = 0L,               // 0 = not set / 0이면 미설정
    val startMin: Int = 9 * 60,             // 09:00 / 출근
    val endMin: Int = 18 * 60,              // 18:00 / 퇴근
    val lunchStartMin: Int = 12 * 60,       // 12:00 / 점심 시작
    val lunchMin: Int = 60,                 // 60 min / 점심 1시간
    val group: String = "",                 // No group / 그룹 없음
)

/**
 * Today's overtime record (button-based).
 * 오늘 야근 기록 (버튼으로 시작/종료).
 *
 * @property date 기록 날짜(yyyy-MM-dd)
 * @property accMs 종료된 구간 합계 ms
 * @property accPay 종료된 구간의 받아야 할 수당 합계
 * @property sinceMs 진행 중 구간 시작 (0이면 정지)
 */
data class OvertimeState(
    val date: String = "",     // Day / 날짜
    val accMs: Long = 0L,      // Closed time / 종료 구간 시간
    val accPay: Double = 0.0,  // Closed pay / 종료 구간 수당
    val sinceMs: Long = 0L,    // Running since / 진행 시작
) {
    /** @return 야근 진행 중이면 true */
    val isRunning: Boolean get() = sinceMs > 0L // Running check / 진행 여부
}

/**
 * Slacking timer record — same shape as overtime (date, closed time/pay, running since).
 * 루팡 기록. 야근 기록과 구조가 같아서 같은 클래스를 재사용 (Java로 치면 같은 DTO 재사용).
 */
typealias LupinState = OvertimeState

/**
 * Finished day's overtime (for week/month totals).
 * 지난 날 야근 결과 (주간·월간 합계용).
 *
 * @property ms 야근 시간
 * @property pay 못 받은 수당
 */
data class DayOvertime(val ms: Long, val pay: Double)

/**
 * DataStore wrapper (DAO role).
 * DataStore 저장소 (Java DAO 역할).
 *
 * @param context 애플리케이션 컨텍스트
 */
class SalaryPrefs(private val context: Context) {

    // Preference keys / 저장 키 모음
    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")           // Nickname / 닉네임
        val OLD_MONTHLY = longPreferencesKey("monthly_salary")    // v0.1 key (migration) / 구버전 키 (이전용)
        val PAY_TYPE = stringPreferencesKey("pay_type")           // Pay type / 급여종류
        val PAY_AMOUNT = longPreferencesKey("pay_amount")         // Amount / 금액
        val START = intPreferencesKey("start_min")                // Clock-in / 출근
        val END = intPreferencesKey("end_min")                    // Clock-out / 퇴근
        val LUNCH_START = intPreferencesKey("lunch_start_min")    // Lunch start / 점심 시작
        val LUNCH_MIN = intPreferencesKey("lunch_min")            // Lunch length / 점심 길이
        val GROUP = stringPreferencesKey("group")                 // Group code / 그룹 코드
        val OT_DATE = stringPreferencesKey("ot_date")             // OT date / 야근 날짜
        val OT_ACC_MS = longPreferencesKey("ot_acc_ms")           // OT time / 야근 시간
        val OT_ACC_PAY = doublePreferencesKey("ot_acc_pay")       // OT pay / 야근 수당
        val OT_SINCE = longPreferencesKey("ot_since")             // OT running since / 야근 시작
        val OT_HISTORY = stringPreferencesKey("ot_history")       // JSON {date:{ms,pay}} / 지난 야근 기록
        val LP_DATE = stringPreferencesKey("lp_date")             // Lupin date / 루팡 날짜
        val LP_ACC_MS = longPreferencesKey("lp_acc_ms")           // Lupin time / 루팡 시간
        val LP_ACC_PAY = doublePreferencesKey("lp_acc_pay")       // Lupin pay / 루팡 금액
        val LP_SINCE = longPreferencesKey("lp_since")             // Lupin running since / 루팡 시작
        val LP_HISTORY = stringPreferencesKey("lp_history")       // JSON {date:{ms,pay}} / 지난 루팡 기록
    }

    /** @return 설정 Flow */
    val settingsFlow: Flow<SalarySettings> = context.dataStore.data.map { p ->
        val old = p[OLD_MONTHLY]                                      // v0.1 monthly / 구버전 월급
        val migrate = p[PAY_AMOUNT] == null && old != null            // Needs migration / 이전 필요
        SalarySettings(
            nickname = p[NICKNAME] ?: "나",                           // Nickname / 닉네임
            payType = if (migrate) PayType.MONTHLY
                      else p[PAY_TYPE]?.let(PayType::valueOf) ?: PayType.ANNUAL, // Type / 종류
            payAmount = p[PAY_AMOUNT] ?: old ?: 0L,                   // Amount / 금액
            startMin = p[START] ?: 9 * 60,                            // Clock-in / 출근
            endMin = p[END] ?: 18 * 60,                               // Clock-out / 퇴근
            lunchStartMin = p[LUNCH_START] ?: 12 * 60,                // Lunch start / 점심 시작
            lunchMin = p[LUNCH_MIN] ?: 60,                            // Lunch length / 점심 길이
            group = p[GROUP] ?: "",                                   // Group / 그룹
        )
    }

    /** @return 오늘 야근 상태 Flow */
    val overtimeFlow: Flow<OvertimeState> = context.dataStore.data.map { p ->
        OvertimeState(p[OT_DATE] ?: "", p[OT_ACC_MS] ?: 0L, p[OT_ACC_PAY] ?: 0.0, p[OT_SINCE] ?: 0L) // Read all / 전체 읽기
    }

    /** @return 지난 야근 기록 Flow (날짜 → 결과) */
    val historyFlow: Flow<Map<String, DayOvertime>> = context.dataStore.data.map { p ->
        parseHistory(p[OT_HISTORY])                                   // JSON → map / JSON 변환
    }

    /** @return 오늘 루팡 상태 Flow */
    val lupinFlow: Flow<LupinState> = context.dataStore.data.map { p ->
        LupinState(p[LP_DATE] ?: "", p[LP_ACC_MS] ?: 0L, p[LP_ACC_PAY] ?: 0.0, p[LP_SINCE] ?: 0L) // Read all / 전체 읽기
    }

    /** @return 지난 루팡 기록 Flow */
    val lupinHistoryFlow: Flow<Map<String, DayOvertime>> = context.dataStore.data.map { p ->
        parseHistory(p[LP_HISTORY])                                   // JSON → map / JSON 변환
    }

    /**
     * Save today's slacking state and optionally the history.
     * 오늘 루팡 상태(및 기록) 저장.
     *
     * @param lp 루팡 상태
     * @param history 바뀐 기록 (null이면 그대로)
     */
    suspend fun saveLupin(lp: LupinState, history: Map<String, DayOvertime>? = null) {
        context.dataStore.edit { p ->
            p[LP_DATE] = lp.date; p[LP_ACC_MS] = lp.accMs; p[LP_ACC_PAY] = lp.accPay; p[LP_SINCE] = lp.sinceMs // State / 상태
            if (history != null) p[LP_HISTORY] = toJson(history)                                         // History / 기록
        }
    }

    /**
     * Save settings.
     * 설정 저장.
     *
     * @param s 설정
     */
    suspend fun saveSettings(s: SalarySettings) {
        context.dataStore.edit { p ->
            p[NICKNAME] = s.nickname; p[PAY_TYPE] = s.payType.name; p[PAY_AMOUNT] = s.payAmount // Basic / 기본
            p[START] = s.startMin; p[END] = s.endMin                                          // Schedule / 시간표
            p[LUNCH_START] = s.lunchStartMin; p[LUNCH_MIN] = s.lunchMin                       // Lunch / 점심
            p[GROUP] = s.group                                                                // Group / 그룹
        }
    }

    /**
     * Save today's overtime state and optionally the history.
     * 오늘 야근 상태(및 기록)를 한 번에 저장.
     *
     * @param ot 야근 상태
     * @param history 바뀐 기록 (null이면 그대로)
     */
    suspend fun saveOvertime(ot: OvertimeState, history: Map<String, DayOvertime>? = null) {
        context.dataStore.edit { p ->
            p[OT_DATE] = ot.date; p[OT_ACC_MS] = ot.accMs; p[OT_ACC_PAY] = ot.accPay; p[OT_SINCE] = ot.sinceMs // State / 상태
            if (history != null) p[OT_HISTORY] = toJson(history)                                         // History / 기록
        }
    }

    /**
     * JSON → history map.
     * JSON을 기록 맵으로.
     *
     * @param raw JSON 문자열
     * @return 기록 맵
     */
    private fun parseHistory(raw: String?): Map<String, DayOvertime> {
        if (raw.isNullOrBlank()) return emptyMap()                        // Nothing / 없음
        val o = JSONObject(raw)                                           // Parse / 파싱
        return o.keys().asSequence().associateWith { k ->
            val d = o.getJSONObject(k)                                    // Day entry / 날짜 항목
            DayOvertime(d.optLong("ms"), d.optDouble("pay", 0.0))         // Map / 변환
        }
    }

    /**
     * History map → JSON (keeps last 62 days only).
     * 기록을 JSON으로 (최근 62일만 보관).
     *
     * @param h 기록 맵
     * @return JSON 문자열
     */
    private fun toJson(h: Map<String, DayOvertime>): String {
        val o = JSONObject()                                              // Root / 루트
        h.keys.sortedDescending().take(62).forEach { k ->                 // Recent only / 최근만
            o.put(k, JSONObject().put("ms", h[k]!!.ms).put("pay", h[k]!!.pay)) // Entry / 항목
        }
        return o.toString()                                               // Serialize / 직렬화
    }
}
