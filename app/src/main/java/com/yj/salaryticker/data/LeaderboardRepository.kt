package com.yj.salaryticker.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * One ranking row. Server stores the *ingredients* (rate, time), not the amount.
 * 랭킹 한 줄. 서버엔 금액이 아니라 계산 재료(초당급여, 시간)만 저장 → 쓰기 횟수 최소화.
 *
 * @property userId 사용자 고유 ID (Firebase 익명 uid)
 * @property nickname 표시 닉네임
 * @property rate 초당 급여
 * @property accMs 정지 전까지 누적 근무 ms
 * @property since 실행 시작 epoch ms (0이면 정지)
 * @property amount 화면 표시용 금액 (클라이언트가 매초 계산해서 채움)
 * @property isMe 내 항목 여부
 */
data class RankEntry(
    val userId: String,          // Unique ID / 고유 ID
    val nickname: String,        // Display name / 닉네임
    val rate: Double,            // Won/sec / 초당 급여
    val accMs: Long,             // Stopped total / 정지 누적
    val since: Long,             // Running since / 시작시각
    val amount: Double = 0.0,    // Computed live / 실시간 계산값
    val isMe: Boolean = false,   // Highlight flag / 내 항목 강조
) {
    /**
     * Earnings at a given time.
     * 특정 시각 기준 금액.
     *
     * @param nowMs 현재 epoch ms
     * @return 누적 금액(원)
     */
    fun amountAt(nowMs: Long): Double {
        val running = if (since > 0) (nowMs - since).coerceIn(0L, MAX_MS) else 0L // Current segment / 현재 구간
        return (accMs + running).coerceAtMost(MAX_MS) / 1000.0 * rate            // ms → won / 금액 환산
    }

    /** @return 지금 근무 중이면 true */
    val isRunning: Boolean get() = since > 0 // Running check / 실행 여부

    companion object {
        const val MAX_MS = 16L * 3600 * 1000 // Daily cap 16h (matches rules) / 하루 16시간 상한 (보안규칙과 동일)
    }
}

/**
 * Ranking data source abstraction.
 * 랭킹 저장소 인터페이스 (Java interface + 구현체 교체 구조).
 */
interface LeaderboardRepository {

    /** @return 내 사용자 ID (로그인 전이면 null) */
    fun myUserId(): String?

    /**
     * Upload my current state (call only when it changes).
     * 내 상태 업로드. 시작/정지/설정 변경 때만 호출.
     *
     * @param nickname 닉네임
     * @param rate 초당 급여
     * @param accMs 누적 ms
     * @param since 시작시각 (0이면 정지)
     */
    suspend fun submitMyState(nickname: String, rate: Double, accMs: Long, since: Long)

    /**
     * Observe today's participants in real time.
     * 오늘 참가자 실시간 구독.
     *
     * @return 참가자 목록 Flow (정렬은 금액 계산 후 ViewModel에서)
     */
    fun observeRanking(): Flow<List<RankEntry>>
}

/**
 * Firestore implementation: daily_rankings/{yyyy-MM-dd}/users/{uid}
 * Firestore 구현체. 날짜별 컬렉션에 uid 문서 하나씩.
 */
class FirestoreLeaderboardRepository : LeaderboardRepository {

    private val auth = FirebaseAuth.getInstance()     // Auth / 인증
    private val db = FirebaseFirestore.getInstance()  // DB / 파이어스토어

    /** @return 오늘 날짜 users 컬렉션 */
    private fun todayUsers() = db.collection("daily_rankings")
        .document(LocalDate.now().toString())         // Day doc / 날짜 문서
        .collection("users")                          // Users / 사용자 목록

    /** @return 현재 uid */
    override fun myUserId(): String? = auth.currentUser?.uid // Logged-in uid / 로그인 uid

    /**
     * Sign in anonymously if needed.
     * 필요하면 익명 로그인.
     *
     * @return uid
     */
    private suspend fun ensureLogin(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid // Reuse or create / 재사용 또는 생성

    /**
     * @param nickname 닉네임
     * @param rate 초당 급여
     * @param accMs 누적 ms
     * @param since 시작시각
     */
    override suspend fun submitMyState(nickname: String, rate: Double, accMs: Long, since: Long) {
        try {
            val uid = ensureLogin()                                    // Login / 로그인
            todayUsers().document(uid).set(
                mapOf(
                    "nickname" to nickname.take(12),                   // Rule: ≤12 chars / 규칙: 12자 이하
                    "rate" to rate.coerceIn(0.0, 100.0),               // Rule: 0..100 / 규칙: 0~100
                    "accMs" to accMs.coerceIn(0L, RankEntry.MAX_MS),   // Rule: ≤16h / 규칙: 16시간 이하
                    "since" to since,                                  // Start time / 시작시각
                )
            ).await()                                                  // Upsert / 덮어쓰기
        } catch (e: Exception) {
            Log.w(TAG, "submit failed", e)                             // Offline etc. / 오프라인 등은 무시
        }
    }

    /** @return 오늘 참가자 실시간 Flow */
    override fun observeRanking(): Flow<List<RankEntry>> = callbackFlow {
        val reg = todayUsers()
            .limit(200)                                                // Cap reads / 읽기량 제한
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listen failed", err); return@addSnapshotListener } // Ignore error / 오류 무시
                val me = auth.currentUser?.uid                         // My uid / 내 uid
                val list = snap?.documents.orEmpty().map { d ->
                    RankEntry(
                        userId = d.id,                                 // Doc ID = uid / 문서ID = uid
                        nickname = d.getString("nickname") ?: "?",     // Name / 이름
                        rate = d.getDouble("rate") ?: 0.0,             // Rate / 초당
                        accMs = d.getLong("accMs") ?: 0L,              // Acc / 누적
                        since = d.getLong("since") ?: 0L,              // Since / 시작
                        isMe = d.id == me,                             // Mark me / 내 항목
                    )
                }
                trySend(list)                                          // Push / 방출
            }
        awaitClose { reg.remove() }                                    // Unsubscribe / 구독 해제
    }

    private companion object {
        const val TAG = "Leaderboard" // Log tag / 로그 태그
    }
}
