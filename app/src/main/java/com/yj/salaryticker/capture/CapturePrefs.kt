package com.yj.salaryticker.capture

import android.content.Context
import android.content.SharedPreferences

/**
 * Capture settings and recent results (SharedPreferences: shared by app UI and the service).
 * 캡처 설정과 최근 결과. 앱 화면과 접근성 서비스가 같이 쓰므로 단순한 SharedPreferences 사용.
 *
 * @param context 컨텍스트
 */
class CapturePrefs(context: Context) {

    val sp: SharedPreferences = context.getSharedPreferences("capture", Context.MODE_PRIVATE) // Store / 저장소

    /** 빠른 모드 여부 (false면 안전 모드) */
    var fastMode: Boolean
        get() = sp.getBoolean(KEY_FAST, true)                                   // Default fast / 기본 빠름
        set(v) = sp.edit().putBoolean(KEY_FAST, v).apply()                      // Save / 저장

    /** 플로팅 버튼 표시 여부 */
    var showButton: Boolean
        get() = sp.getBoolean(KEY_BUTTON, true)                                 // Default shown / 기본 표시
        set(v) = sp.edit().putBoolean(KEY_BUTTON, v).apply()                    // Save / 저장

    /** 최근 저장된 이미지 URI 목록 (최신순, 최대 10개) */
    val recent: List<String>
        get() = sp.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() } // Parse / 파싱

    /**
     * Add saved image URIs to the front of the recent list.
     * 저장된 이미지들을 최근 목록 앞에 추가.
     *
     * @param uris 새 URI들
     */
    fun addRecent(uris: List<String>) {
        sp.edit().putString(KEY_RECENT, (uris + recent).distinct().take(10).joinToString("\n")).apply() // Keep 10 / 10개 유지
    }

    companion object {
        const val KEY_FAST = "fast"       // Mode key / 모드 키
        const val KEY_BUTTON = "button"   // Button key / 버튼 키
        const val KEY_RECENT = "recent"   // Recent key / 최근 키
    }
}
