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
     * @return "12,345.67" 형태 문자열
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
     * @return "02:13:45" 형태 문자열
     */
    fun hms(ms: Long): String {
        val t = ms / 1000                                          // Total seconds / 총 초
        return "%02d:%02d:%02d".format(t / 3600, t % 3600 / 60, t % 60) // H:M:S / 시:분:초
    }
}
