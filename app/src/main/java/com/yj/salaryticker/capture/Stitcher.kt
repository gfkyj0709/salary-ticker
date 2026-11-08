package com.yj.salaryticker.capture

/**
 * Pure stitching math on per-row hashes (no Android dependency → unit-testable).
 * 행(row) 해시 배열만으로 이어 붙이기 계산 (안드로이드 의존 없음 → 단위테스트 가능).
 *
 * 개념: 화면 한 줄(가로 1px 행)을 숫자 하나(해시)로 요약 → 두 캡처의 해시 배열을 위아래로 밀어가며
 * 가장 잘 겹치는 위치(= 스크롤된 픽셀 수)를 찾음. Java로 치면 문자열 두 개의 겹치는 부분 찾기와 같음.
 */
object Stitcher {

    /** Rows treated as blank (single color) get this hash. / 단색 행 표시값 */
    const val BLANK = Long.MIN_VALUE

    /**
     * Result of matching two frames.
     * 두 캡처 비교 결과.
     *
     * @property shift 새 화면이 위로 이동한 픽셀 수 (0이면 안 움직임 = 끝)
     * @property score 겹친 구간 일치율 (0~1)
     */
    data class Match(val shift: Int, val score: Double)

    /**
     * Number of identical rows from the top (fixed header like toolbar/status bar).
     * 위에서부터 같은 행이 몇 줄인지 = 고정 상단바 높이.
     *
     * @param a 이전 화면 행 해시
     * @param b 다음 화면 행 해시
     * @return 고정 상단 높이(px)
     */
    fun staticTop(a: LongArray, b: LongArray): Int {
        var i = 0                                                        // Row index / 행 번호
        while (i < a.size && i < b.size && a[i] == b[i]) i++             // Same row → fixed / 같으면 고정
        return i
    }

    /**
     * Number of identical rows from the bottom (fixed footer like bottom tabs).
     * 아래에서부터 같은 행이 몇 줄인지 = 고정 하단바 높이.
     *
     * @param a 이전 화면
     * @param b 다음 화면
     * @return 고정 하단 높이(px)
     */
    fun staticBottom(a: LongArray, b: LongArray): Int {
        var i = 0                                                        // Count / 개수
        while (i < a.size && i < b.size && a[a.size - 1 - i] == b[b.size - 1 - i]) i++ // From bottom / 아래부터
        return i
    }

    /**
     * Find how far content scrolled between two content areas.
     * 두 콘텐츠 영역 사이에서 스크롤된 거리 찾기.
     *
     * shift = s 이면: 새 화면의 y행 == 이전 화면의 (y + s)행.
     * 단색(빈) 행은 어디든 일치하므로 점수에서 제외. 동점이면 예상 거리(제스처 거리)에 가까운 값 선택.
     *
     * @param a 이전 화면 콘텐츠 행 해시
     * @param b 새 화면 콘텐츠 행 해시 (a와 같은 길이)
     * @param expected 예상 이동 거리 (동점 처리용)
     * @param minOverlapRatio 최소 겹침 비율
     * @return 가장 그럴듯한 이동 결과 (못 찾으면 score 0)
     */
    fun findShift(a: LongArray, b: LongArray, expected: Int, minOverlapRatio: Double = 0.15): Match {
        val h = minOf(a.size, b.size)                                       // Height / 높이
        val maxShift = (h - h * minOverlapRatio).toInt()                    // Keep some overlap / 최소 겹침 보장
        val scores = DoubleArray(maxShift + 1)                              // Score per shift / 이동량별 점수
        for (s in 0..maxShift) {
            var compared = 0; var same = 0                                  // Counters / 카운터
            for (y in 0 until h - s) {
                val rb = b[y]                                               // New row / 새 행
                if (rb == BLANK) continue                                   // Skip blank / 빈 행 제외
                compared++                                                  // Count / 비교 수
                if (a[y + s] == rb) same++                                  // Match / 일치
            }
            scores[s] = if (compared >= MIN_COMPARED) same.toDouble() / compared else 0.0 // Ratio / 일치율
        }
        val best = scores.maxOrNull() ?: 0.0                                // Best score / 최고점
        if (best <= 0.0) return Match(0, 0.0)                               // Nothing / 실패
        // Among near-best shifts pick the one closest to expected / 최고점 근처 중 예상 거리에 가장 가까운 값
        val s = scores.indices.filter { scores[it] >= best - 0.02 }.minByOrNull { kotlin.math.abs(it - expected) }!!
        return Match(s, scores[s])
    }

    /**
     * Fallback for animated screens: best shift only within expected ±[tolerance].
     * 애니메이션 화면용 보조 탐색: 예상 거리 ±[tolerance] 범위 안에서만 최고점 찾기.
     * 관성 없는 스와이프라 실제 이동 ≈ 스와이프 거리 → 일부 행이 달라도 위치를 좁혀서 찾을 수 있음.
     *
     * @param a 이전 화면 콘텐츠 행 해시
     * @param b 새 화면 콘텐츠 행 해시
     * @param expected 예상 이동 거리
     * @param tolerance 허용 오차 비율 (0.3 = ±30%)
     * @return 범위 내 최고 결과
     */
    fun findShiftNear(a: LongArray, b: LongArray, expected: Int, tolerance: Double = 0.3): Match {
        val h = minOf(a.size, b.size)                                        // Height / 높이
        val lo = (expected * (1 - tolerance)).toInt().coerceAtLeast(1)       // Lower bound / 하한
        val hi = (expected * (1 + tolerance)).toInt().coerceAtMost(h - MIN_COMPARED) // Upper bound / 상한
        var best = Match(0, 0.0)                                             // Best so far / 현재 최고
        for (s in lo..hi) {
            var compared = 0; var same = 0                                   // Counters / 카운터
            for (y in 0 until h - s) {
                val rb = b[y]; if (rb == BLANK) continue                     // Skip blank / 빈 행 제외
                compared++; if (a[y + s] == rb) same++                       // Compare / 비교
            }
            val score = if (compared >= MIN_COMPARED) same.toDouble() / compared else 0.0 // Ratio / 일치율
            if (score > best.score) best = Match(s, score)                   // Keep best / 최고 갱신
        }
        return best
    }

    private const val MIN_COMPARED = 8 // Need at least 8 non-blank rows / 최소 비교 행 수
}
