package com.yj.salaryticker.ui

import java.text.NumberFormat
import java.util.Locale

/**
 * Display formatting helpers.
 * 화면 표시용 포맷 유틸.
 */
object Format {

    /**
     * Won with thousands separator.
     * 천 단위 콤마 금액.
     *
     * @param v 금액
     * @param decimals 소수 자릿수
     * @return "12,345.67" 형태
     */
    fun won(v: Double, decimals: Int = 0): String =
        NumberFormat.getNumberInstance(Locale.KOREA).apply {
            minimumFractionDigits = decimals // Fixed decimals / 소수 고정
            maximumFractionDigits = decimals // Fixed decimals / 소수 고정
        }.format(v)                          // Format / 포맷

    /**
     * Millis to HH:MM:SS.
     * 밀리초 → 시:분:초.
     *
     * @param ms 밀리초
     * @return "02:13:45" 형태
     */
    fun hms(ms: Long): String {
        val t = ms.coerceAtLeast(0L) / 1000                              // Total seconds / 총 초
        return "%02d:%02d:%02d".format(t / 3600, t % 3600 / 60, t % 60) // H:M:S / 시:분:초
    }

    /**
     * Minute-of-day to HH:MM.
     * 하루 중 분 → "09:00".
     *
     * @param min 분
     * @return 시각 문자열
     */
    fun hm(min: Int): String = "%02d:%02d".format(min / 60, min % 60) // H:M / 시:분

    /**
     * Parse "HH:MM" (or "HHMM", "9") to minute-of-day.
     * "09:00", "0900", "9" 같은 입력을 분으로 변환.
     *
     * @param text 입력
     * @return 분 (잘못되면 null)
     */
    fun parseHm(text: String): Int? {
        val digits = text.filter(Char::isDigit)                                    // Digits only / 숫자만
        val (h, m) = when (digits.length) {
            1, 2 -> digits.toInt() to 0                                            // "9" → 9:00
            3 -> digits.take(1).toInt() to digits.drop(1).toInt()                  // "930" → 9:30
            4 -> digits.take(2).toInt() to digits.drop(2).toInt()                  // "0930" → 9:30
            else -> return null                                                    // Invalid / 잘못됨
        }
        return if (h in 0..23 && m in 0..59) h * 60 + m else null                  // Validate / 검증
    }
}
