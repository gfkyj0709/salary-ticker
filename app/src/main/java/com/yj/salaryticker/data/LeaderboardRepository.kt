package com.yj.salaryticker.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar

/**
 * One ranking row.
 * 랭킹 한 줄.
 *
 * @property userId 사용자 고유 ID
 * @property nickname 표시 닉네임
 * @property amount 오늘 누적 금액(원)
 * @property isMe 내 항목 여부
 */
data class RankEntry(
    val userId: String,      // Unique ID / 고유 ID
    val nickname: String,    // Display name / 닉네임
    val amount: Double,      // Today's earnings / 오늘 누적액
    val isMe: Boolean = false, // Highlight flag / 내 항목 강조용
)

/**
 * Ranking data source abstraction.
 * 랭킹 저장소 인터페이스 (Java interface + 구현체 교체 구조).
 * 지금은 Fake, 나중에 Firestore 구현체로 교체.
 */
interface LeaderboardRepository {

    /**
     * Upload my today's score.
     * 내 오늘 누적액 업로드.
     *
     * @param nickname 닉네임
     * @param amount 누적 금액
     */
    suspend fun submitMyScore(nickname: String, amount: Double)

    /**
     * Observe today's ranking.
     * 오늘 랭킹 실시간 구독.
     *
     * @return 금액 내림차순 랭킹 Flow
     */
    fun observeRanking(): Flow<List<RankEntry>>
}

/**
 * Local fake with sample rivals (no server needed).
 * 서버 없이 샘플 경쟁자로 동작하는 가짜 구현체.
 * 경쟁자는 오전 9시 출근 가정으로 시간에 따라 금액이 증가.
 */
class FakeLeaderboardRepository : LeaderboardRepository {

    // Sample rivals: nickname to won-per-second / 샘플 경쟁자: 닉네임 → 초당 급여
    private val rivals = listOf(
        "연봉협상승리자" to 9.8,  // High earner / 고연봉
        "칼퇴요정" to 6.2,        // Mid / 중간
        "주니어개발자" to 4.1,     // Junior / 주니어
        "알바왕" to 2.8,          // Part-timer / 알바
    )

    @Volatile private var myScore: RankEntry? = null // Last submitted score / 마지막 업로드 점수

    /**
     * Keep my score in memory.
     * 메모리에 내 점수 보관.
     *
     * @param nickname 닉네임
     * @param amount 누적 금액
     */
    override suspend fun submitMyScore(nickname: String, amount: Double) {
        myScore = RankEntry(ME_ID, nickname, amount, isMe = true) // Store latest / 최신값 저장
    }

    /**
     * Emit rivals + me every second.
     * 1초마다 경쟁자 + 내 점수를 정렬해서 방출.
     *
     * @return 랭킹 Flow
     */
    override fun observeRanking(): Flow<List<RankEntry>> = flow {
        while (true) {                                              // Loop until collector cancels / 구독 취소될 때까지
            val worked = secondsSinceNineAm()                       // Rivals' worked seconds / 경쟁자 근무 초
            val list = rivals.mapIndexed { i, (name, rate) ->
                RankEntry("rival_$i", name, worked * rate)          // Rival amount / 경쟁자 금액
            } + listOfNotNull(myScore)                              // Add me if exists / 내 점수 추가
            emit(list.sortedByDescending { it.amount })             // Sort desc / 내림차순
            delay(1_000L)                                           // 1s period / 1초 주기
        }
    }

    /**
     * Seconds since 09:00 today, capped at 8 hours.
     * 오늘 09시부터 지난 초 (최대 8시간).
     *
     * @return 근무 초
     */
    private fun secondsSinceNineAm(): Double {
        val c = Calendar.getInstance()                                    // Now / 현재
        val nine = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)         // 09:00 / 9시
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)         // Clear sec/ms / 초·밀리초 0
        }
        val sec = (c.timeInMillis - nine.timeInMillis) / 1000.0           // Diff seconds / 차이(초)
        return sec.coerceIn(0.0, 8 * 3600.0)                              // Clamp 0..8h / 0~8시간 제한
    }

    companion object {
        const val ME_ID = "me" // Local user ID / 로컬 사용자 ID
    }
}
