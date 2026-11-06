package com.yj.salaryticker.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * One participant as stored on the server: calculation *ingredients*, not amounts.
 * 서버에 저장되는 참가자 1명. 금액이 아니라 계산 재료만 저장 → 상태 바뀔 때만 쓰기.
 * 정규 근무는 시간표로 누구나 계산 가능, 야근은 기록값을 공유.
 *
 * @property uid 사용자 ID (Firebase 익명 uid)
 * @property nickname 닉네임
 * @property group 그룹 코드
 * @property monthly 월급(원)
 * @property startMin 출근(분)
 * @property endMin 퇴근(분)
 * @property lunchStartMin 점심 시작(분)
 * @property lunchMin 점심 길이(분)
 * @property hourly 통상시급
 * @property otDate 오늘 야근 기록 날짜
 * @property otAccMs 오늘 종료된 야근 ms
 * @property otAccPay 오늘 종료된 야근 수당
 * @property otSince 진행 중 야근 시작 (0이면 정지)
 * @property weekKey 주간 기준 키
 * @property otWeekBase 이번 주 지난 날 야근 수당 합
 * @property monthKey 월간 기준 키
 * @property otMonthBase 이번 달 지난 날 야근 수당 합
 * @property lastDay 마지막 접속일 (오래된 사용자 숨김용)
 */
data class RemoteUser(
    val uid: String,
    val nickname: String,
    val group: String,
    val monthly: Double,
    val startMin: Int,
    val endMin: Int,
    val lunchStartMin: Int,
    val lunchMin: Int,
    val hourly: Double,
    val otDate: String,
    val otAccMs: Long,
    val otAccPay: Double,
    val otSince: Long,
    val weekKey: String,
    val otWeekBase: Double,
    val monthKey: String,
    val otMonthBase: Double,
    val lastDay: String,
) {
    /**
     * Convert to settings so the same calculator works for everyone.
     * 같은 계산기를 쓰기 위해 설정 객체로 변환.
     *
     * @return 급여 설정
     */
    fun toSettings() = SalarySettings(
        nickname = nickname, payType = PayType.MONTHLY, payAmount = monthly.toLong(), // Monthly basis / 월급 기준
        startMin = startMin, endMin = endMin, lunchStartMin = lunchStartMin, lunchMin = lunchMin, group = group,
    )

    /** @return Firestore 저장용 맵 */
    fun toMap(): Map<String, Any> = mapOf(
        "nickname" to nickname.take(12), "group" to group.take(8),               // Text / 문자
        "monthly" to monthly.coerceIn(0.0, 100_000_000.0),                       // Rule cap / 규칙 상한
        "startMin" to startMin, "endMin" to endMin,                              // Schedule / 시간표
        "lunchStartMin" to lunchStartMin, "lunchMin" to lunchMin,                // Lunch / 점심
        "hourly" to hourly.coerceIn(0.0, 500_000.0),                             // Hourly / 통상시급
        "otDate" to otDate, "otAccMs" to otAccMs.coerceIn(0L, MAX_OT_MS),        // OT today / 오늘 야근
        "otAccPay" to otAccPay.coerceIn(0.0, 1_000_000.0), "otSince" to otSince, // OT pay, since / 수당, 시작
        "weekKey" to weekKey, "otWeekBase" to otWeekBase.coerceIn(0.0, 5_000_000.0),     // Week / 주간
        "monthKey" to monthKey, "otMonthBase" to otMonthBase.coerceIn(0.0, 20_000_000.0), // Month / 월간
        "lastDay" to lastDay,                                                    // Last seen / 마지막 접속
    )

    companion object {
        const val MAX_OT_MS = 16L * 3600 * 1000 // OT cap per day 16h / 하루 야근 상한

        /**
         * Firestore document → model (missing fields → safe defaults).
         * 문서를 모델로 변환 (없는 필드는 안전한 기본값).
         *
         * @param d 문서
         * @return 참가자
         */
        fun from(d: DocumentSnapshot) = RemoteUser(
            uid = d.id,
            nickname = d.getString("nickname") ?: "?",
            group = d.getString("group") ?: "",
            monthly = d.getDouble("monthly") ?: 0.0,
            startMin = (d.getLong("startMin") ?: 540L).toInt(),
            endMin = (d.getLong("endMin") ?: 1080L).toInt(),
            lunchStartMin = (d.getLong("lunchStartMin") ?: 720L).toInt(),
            lunchMin = (d.getLong("lunchMin") ?: 60L).toInt(),
            hourly = d.getDouble("hourly") ?: 0.0,
            otDate = d.getString("otDate") ?: "",
            otAccMs = d.getLong("otAccMs") ?: 0L,
            otAccPay = d.getDouble("otAccPay") ?: 0.0,
            otSince = d.getLong("otSince") ?: 0L,
            weekKey = d.getString("weekKey") ?: "",
            otWeekBase = d.getDouble("otWeekBase") ?: 0.0,
            monthKey = d.getString("monthKey") ?: "",
            otMonthBase = d.getDouble("otMonthBase") ?: 0.0,
            lastDay = d.getString("lastDay") ?: "",
        )
    }
}

/**
 * Ranking data source abstraction.
 * 랭킹 저장소 인터페이스 (Java interface + 구현체 구조).
 */
interface LeaderboardRepository {

    /** @return 내 uid (로그인 전이면 null) */
    fun myUserId(): String?

    /**
     * Upload my state (call only when it changes).
     * 내 상태 업로드 (바뀔 때만).
     *
     * @param me 내 정보 (uid는 무시하고 로그인 uid 사용)
     */
    suspend fun submit(me: RemoteUser)

    /** @return 전체 참가자 실시간 Flow */
    fun observeAll(): Flow<List<RemoteUser>>
}

/**
 * Firestore implementation: users_v2/{uid} (one doc per user).
 * Firestore 구현체: 사용자당 문서 1개.
 */
class FirestoreLeaderboardRepository : LeaderboardRepository {

    private val auth = FirebaseAuth.getInstance()                  // Auth / 인증
    private val users = FirebaseFirestore.getInstance().collection("users_v2") // Collection / 컬렉션

    /** @return 현재 uid */
    override fun myUserId(): String? = auth.currentUser?.uid      // Logged-in uid / 로그인 uid

    /**
     * @param me 내 정보
     */
    override suspend fun submit(me: RemoteUser) {
        try {
            val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid // Login / 로그인
            users.document(uid).set(me.toMap()).await()                                   // Upsert / 덮어쓰기
        } catch (e: Exception) {
            Log.w(TAG, "submit failed", e)                                                // Offline etc. / 오프라인 등
        }
    }

    /** @return 참가자 Flow (최대 500명) */
    override fun observeAll(): Flow<List<RemoteUser>> = callbackFlow {
        val reg = users.limit(500).addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "listen failed", err); return@addSnapshotListener } // Ignore / 무시
            trySend(snap?.documents.orEmpty().map(RemoteUser::from))                        // Push / 방출
        }
        awaitClose { reg.remove() }                                                         // Unsubscribe / 해제
    }

    private companion object {
        const val TAG = "Leaderboard" // Log tag / 로그 태그
    }
}
