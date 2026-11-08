package com.yj.salaryticker.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.MediaStore
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Long-screenshot service: floating 📸 button over any app → auto scroll + capture + stitch → gallery.
 * 긴 화면 캡처 서비스. 다른 앱 위의 📸 버튼 → 자동 스크롤·캡처·이어붙이기 → 갤러리 저장.
 *
 * 왜 접근성 서비스? 다른 앱을 스크롤(제스처)하고 화면을 찍는 걸 한 곳에서 할 수 있고,
 * Android 11+ takeScreenshot()은 매번 '화면 녹화 허용' 팝업이 안 뜸.
 */
class CaptureService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)  // Service-scoped coroutines / 서비스 코루틴
    private lateinit var prefs: CapturePrefs                                // Settings / 설정
    private var button: TextView? = null                                    // Floating button / 플로팅 버튼
    private var job: Job? = null                                            // Running capture / 진행 중 캡처
    @Volatile private var stopRequested = false                             // Volume-key stop / 볼륨키 중지 요청

    // Show/hide button when the setting changes / 설정 바뀌면 버튼 표시 갱신
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CapturePrefs.KEY_BUTTON) refreshButton()                 // Button toggle / 버튼 토글
    }

    /** Service connected: prepare button. / 서비스 연결 시 버튼 준비 */
    override fun onServiceConnected() {
        super.onServiceConnected()                                          // Parent / 부모
        prefs = CapturePrefs(this)                                          // Load prefs / 설정 로드
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)     // Watch / 감시
        refreshButton()                                                     // Show if enabled / 표시
    }

    /** @param event 사용 안 함 (제스처·캡처만 씀) */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit   // Not used / 미사용

    /** Required callback. / 필수 콜백 */
    override fun onInterrupt() = Unit                                       // Not used / 미사용

    /** Clean up. / 정리 */
    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)   // Unwatch / 감시 해제
        removeButton()                                                      // Remove overlay / 버튼 제거
        scope.cancel()                                                      // Stop coroutines / 코루틴 중지
        super.onDestroy()
    }

    /**
     * Volume-down stops a running capture.
     * 캡처 중 볼륨 아래 키 → 중지 (평소엔 볼륨 그대로 동작).
     *
     * @param event 키 이벤트
     * @return 소비했으면 true
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (job?.isActive == true && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { // Capturing + vol down / 캡처 중 볼륨↓
            if (event.action == KeyEvent.ACTION_DOWN) stopRequested = true             // Request stop / 중지 요청
            return true                                                                // Consume / 볼륨 안 바뀌게
        }
        return false                                                                   // Pass through / 그대로 전달
    }

    // ---------------- Floating button / 플로팅 버튼 ----------------

    /** Add or remove the button by setting. / 설정에 따라 버튼 추가·제거 */
    private fun refreshButton() {
        if (prefs.showButton) addButton() else removeButton()             // Toggle / 토글
    }

    /** Add draggable 📸 button (accessibility overlay needs no extra permission). / 드래그 가능한 버튼 추가 */
    @SuppressLint("ClickableViewAccessibility")
    private fun addButton() {
        if (button != null) return                                          // Already shown / 이미 있음
        val wm = getSystemService(WindowManager::class.java)                // Window manager / 윈도우 매니저
        val size = (56 * resources.displayMetrics.density).toInt()         // 56dp / 버튼 크기
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Overlay type / 오버레이
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = resources.displayMetrics.widthPixels - size; y = resources.displayMetrics.heightPixels / 3 }
        val v = TextView(this).apply {
            text = "📸"; textSize = 24f; gravity = Gravity.CENTER; alpha = 0.85f            // Look / 모양
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#7452C0")) }
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false // Drag state / 드래그 상태
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY                       // Delta / 이동량
                    if (moved || dx * dx + dy * dy > 400) {                                 // >20px = drag / 드래그 판정
                        moved = true; lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); wm.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) startCapture()                          // Tap = start / 탭 = 시작
            }
            true                                                                             // Handled / 처리함
        }
        wm.addView(v, lp)                                                   // Show / 표시
        button = v                                                          // Keep ref / 참조 저장
    }

    /** Remove the button. / 버튼 제거 */
    private fun removeButton() {
        button?.let { getSystemService(WindowManager::class.java).removeView(it) } // Remove / 제거
        button = null
    }

    // ---------------- Capture loop / 캡처 루프 ----------------

    /** Start capturing the app under the button. / 캡처 시작 */
    private fun startCapture() {
        if (job?.isActive == true) return                                    // Already running / 진행 중
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {                 // Needs Android 11 / 11 이상 필요
            toast("안드로이드 11 이상에서만 돼요"); return
        }
        stopRequested = false                                               // Reset / 초기화
        job = scope.launch {
            button?.visibility = android.view.View.GONE                     // Hide button from shots / 캡처에 안 찍히게
            toast("끝까지 자동으로 내려요 · 볼륨 아래 키로 중지")
            delay(600)                                                      // Let toast/button settle / 잠깐 대기
            val saved = try { captureLoop() } catch (e: Exception) { toast("캡처 오류: ${e.javaClass.simpleName} ${e.message}"); emptyList() }
            button?.visibility = android.view.View.VISIBLE                  // Show again / 다시 표시
            if (saved.isNotEmpty()) {
                prefs.addRecent(saved)                                      // Remember / 최근 목록
                delay(2200)                                                 // After diagnostics toast / 진단 토스트 뒤에
                toast("저장 완료: 이미지 ${saved.size}장 (갤러리 › SalaryTicker)")
            }
        }
    }

    /**
     * Scroll-capture-stitch until the end, stop key, or limit.
     * 끝·중지·한도까지 스크롤 → 캡처 → 이어붙이기 반복.
     *
     * 안정성 장치: (1) 맞추기 실패 시 0.4초 뒤 재촬영 최대 3번 (2) '안 움직임'이 2번 연속일 때만 끝으로 판단.
     *
     * @return 저장된 이미지 URI 목록
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureLoop(): List<String> {
        val fast = prefs.fastMode                                           // Mode / 모드
        val moveMs = if (fast) 320L else 480L                               // Swipe time (incl. wiggle tail) / 스와이프 시간
        val settleMs = if (fast) 150L else 900L                             // Wait for render / 렌더 대기
        val first = shot() ?: run { toast("이 화면은 캡처가 막혀 있어요"); return emptyList() } // Secure app / 캡처 차단 앱
        val w = first.width; val h = first.height                           // Size / 크기
        val writer = PartWriter(w)                                          // Output builder / 결과 작성기
        var prev = first; var prevH = rowHashes(first)                      // Previous frame / 이전 화면
        var top = -1; var bottom = 0                                        // Fixed bars (unknown yet) / 고정 바 (아직 모름)
        var still = 0                                                       // Consecutive no-move count / 연속 안 움직임
        var steps = 0                                                       // Successful scrolls / 성공한 스크롤 수
        var reason = "끝 도달"                                              // Stop reason / 종료 이유
        loop@ for (step in 0 until MAX_STEPS) {
            if (stopRequested) { reason = "사용자 중지"; break }            // User stop / 사용자 중지
            val contentH = if (top < 0) h else h - top - bottom             // Content height / 콘텐츠 높이
            val dist = (contentH * 0.5).toInt()                             // Swipe distance / 스와이프 거리
            val fromY = (if (top < 0) h * 0.75 else top + contentH * 0.8).toFloat() // Start Y / 시작 Y
            swipe(w / 2f, fromY, fromY - dist, moveMs)                      // Scroll / 스크롤
            delay(settleMs)                                                 // Settle / 대기
            var cur = shot() ?: run { reason = "캡처 실패"; null } ?: break // Capture / 캡처
            var curH = rowHashes(cur)                                       // Hash rows / 행 해시

            if (top < 0) {                                                  // First pair: find fixed bars / 첫 비교: 고정 바 찾기
                if (prevH.contentEquals(curH)) {                            // Did not move / 안 움직임
                    cur.recycle()
                    if (++still >= 2) { reason = "스크롤 안 되는 화면"; break } else continue@loop // Retry once / 한 번 더
                }
                still = 0
                top = Stitcher.staticTop(prevH, curH)                       // Header / 상단
                bottom = Stitcher.staticBottom(prevH, curH)                 // Footer / 하단
                if (top + bottom > h * 0.8) { top = 0; bottom = 0 }         // Implausible → none / 비정상이면 무시
                writer.add(prev, 0, h - bottom)                             // First frame without footer / 첫 화면 (하단 제외)
            }

            val cH = h - top - bottom                                       // Content height / 콘텐츠 높이
            var m = Stitcher.findShift(prevH.copyOfRange(top, h - bottom), curH.copyOfRange(top, h - bottom), dist, 0.1) // Match / 겹침
            var tries = 0
            while (m.score < 0.6 && tries < 3) {                            // Retry: still moving or loading / 재시도
                delay(400); tries++                                         // Wait / 대기
                val again = shot() ?: break                                 // Re-capture / 재촬영
                cur.recycle(); cur = again; curH = rowHashes(cur)           // Replace / 교체
                m = Stitcher.findShift(prevH.copyOfRange(top, h - bottom), curH.copyOfRange(top, h - bottom), dist, 0.1)
            }
            if (m.score < 0.6) {                                            // Animated screen? / 애니메이션 화면?
                val near = Stitcher.findShiftNear(prevH.copyOfRange(top, h - bottom), curH.copyOfRange(top, h - bottom), dist) // Near expected / 예상 거리 근처
                if (near.score >= 0.25) m = near                            // Accept partial match / 부분 일치 허용
                else { reason = "이어 붙이기 실패 (일치율 ${(m.score * 100).toInt()}%)"; cur.recycle(); break } // Give up / 포기
            }
            if (m.shift == 0) {                                             // No movement / 안 움직임
                cur.recycle()
                if (++still >= 2) break else continue@loop                  // End only if twice / 2번 연속이면 끝
            }
            still = 0
            writer.add(cur, top + cH - m.shift, m.shift)                    // New rows only / 새로 보인 줄만 추가
            steps++
            if (writer.totalHeight > MAX_TOTAL_HEIGHT) { reason = "최대 길이 도달"; prev.recycleIfNot(first); prev = cur; break } // Limit / 한도
            prev.recycleIfNot(first); prev = cur; prevH = curH              // Advance / 다음으로
        }
        if (top < 0) writer.add(prev, 0, h)                                 // Only one frame / 한 장뿐
        else if (bottom > 0) writer.add(prev, h - bottom, bottom)           // Add footer once / 하단 1번 추가
        val total = writer.totalHeight                                      // Final height / 최종 높이
        val uris = withContext(Dispatchers.IO) { writer.finish() }          // Save / 저장
        toast("$reason · 스크롤 ${steps}번 · 길이 ${"%,d".format(total)}px")   // Diagnostics / 진단 정보
        return uris
    }

    /** Recycle bitmap unless it is [keep]. / [keep]이 아니면 메모리 해제 */
    private fun Bitmap.recycleIfNot(keep: Bitmap) { if (this !== keep) recycle() }

    /**
     * Take one screenshot (waits and retries on rate limit).
     * 화면 1장 캡처 (시스템 최소 간격 333ms 제한 시 재시도).
     *
     * @return 소프트웨어 비트맵 (캡처 차단 화면이면 null)
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun shot(): Bitmap? {
        repeat(5) {                                                         // Retry a few times / 몇 번 재시도
            val r = suspendCancellableCoroutine<Pair<Bitmap?, Int>> { c ->
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(s: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(s.hardwareBuffer, s.colorSpace) // Hardware bitmap / 하드웨어 비트맵
                        val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)                 // To software / 소프트웨어로
                        s.hardwareBuffer.close(); hw?.recycle()                           // Free / 해제
                        c.resume(sw to 0)                                                 // Done / 완료
                    }
                    override fun onFailure(code: Int) = c.resume(null to code)            // Error code / 오류 코드
                })
            }
            if (r.first != null) return r.first                                           // Success / 성공
            if (r.second != ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return null        // Real failure / 진짜 실패
            delay(350)                                                                    // Rate limit wait / 간격 대기
        }
        return null
    }

    /**
     * Swipe up, then wiggle in place so the list sees ~zero vertical speed at release (no fling).
     * 위로 스와이프 후 제자리에서 좌우로 미세하게 흔들기 → 손 뗄 때 세로 속도 0 → 관성(플링) 없음.
     * (continueStroke 방식은 일부 기기에서 다음 제스처가 막혀서 한 번의 제스처로 처리)
     *
     * @param x X좌표
     * @param fromY 시작 Y
     * @param toY 끝 Y
     * @param moveMs 전체 시간 (이동 + 흔들기)
     */
    private suspend fun swipe(x: Float, fromY: Float, toY: Float, moveMs: Long) {
        val path = Path().apply {
            moveTo(x, fromY); lineTo(x, toY)                                // Main move / 본 이동
            for (i in 1..WIGGLES) lineTo(x + if (i % 2 == 1) 12f else -12f, toY) // Wiggle ±12px (< touch slop) / 미세 흔들기
            lineTo(x, toY)                                                  // Back to center / 원위치
        }
        dispatch(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, moveMs)).build()) // Run / 실행
    }

    /**
     * Dispatch a gesture and wait for it to finish.
     * 제스처 실행 후 끝날 때까지 대기.
     *
     * @param g 제스처
     */
    private suspend fun dispatch(g: GestureDescription) = suspendCancellableCoroutine<Unit> { c ->
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(Unit) } // Done / 완료
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(Unit) } // Cancelled / 취소
        }, null)
        if (!ok && c.isActive) c.resume(Unit)                                             // Rejected / 거부
    }

    /**
     * Per-row hashes (sample every 3rd pixel, skip right 5% where scrollbars appear).
     * 행 해시: 3픽셀마다 샘플, 스크롤바가 생기는 오른쪽 5%는 제외.
     *
     * @param b 비트맵
     * @return 행 해시 배열
     */
    private fun rowHashes(b: Bitmap): LongArray {
        val w = b.width; val h = b.height                                   // Size / 크기
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) } // All pixels / 전체 픽셀
        val xEnd = (w * 0.95).toInt()                                       // Skip scrollbar / 스크롤바 제외
        return LongArray(h) { y ->
            val base = y * w; val first = px[base]                          // Row start / 행 시작
            var hash = 1125899906842597L; var uniform = true                // FNV-ish / 해시 초기값
            var x = 0
            while (x < xEnd) {
                val p = px[base + x]                                        // Pixel / 픽셀
                if (p != first) uniform = false                             // Not blank / 단색 아님
                hash = 31 * hash + p                                        // Mix / 섞기
                x += 3                                                      // Sample / 샘플 간격
            }
            if (uniform) Stitcher.BLANK else hash                           // Blank marker / 빈 행 표시
        }
    }

    /** @param msg 표시할 문구 */
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() // Toast / 토스트

    /**
     * Draws rows into fixed-height part images and saves them (keeps memory bounded).
     * 정해진 높이의 이미지에 줄을 그려 넣고, 차면 저장 (메모리 사용량 제한).
     *
     * @param width 이미지 폭
     */
    private inner class PartWriter(private val width: Int) {
        private var part: Bitmap? = null                                    // Current part / 현재 조각
        private var used = 0                                                // Filled rows / 채운 줄
        private val uris = mutableListOf<String>()                          // Saved / 저장된 URI
        private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date()) // File time / 파일 시각
        var totalHeight = 0; private set                                    // All rows / 전체 줄 수

        /**
         * Append rows [srcY, srcY+n) of [src].
         * 원본의 [srcY, srcY+n) 줄을 이어 붙임.
         *
         * @param src 원본
         * @param srcY 시작 줄
         * @param n 줄 수
         */
        fun add(src: Bitmap, srcY: Int, n: Int) {
            var y = srcY; var left = n                                      // Cursor / 커서
            while (left > 0) {
                val p = part ?: Bitmap.createBitmap(width, PART_HEIGHT, Bitmap.Config.ARGB_8888).also { part = it; used = 0 } // New part / 새 조각
                val k = minOf(left, PART_HEIGHT - used)                     // Fit / 들어갈 만큼
                Canvas(p).drawBitmap(src, Rect(0, y, width, y + k), Rect(0, used, width, used + k), null) // Copy rows / 줄 복사
                used += k; y += k; left -= k; totalHeight += k              // Advance / 진행
                if (used == PART_HEIGHT) save()                             // Full → save / 꽉 차면 저장
            }
        }

        /** @return 남은 조각까지 저장한 URI 목록 */
        fun finish(): List<String> { if (used > 0) save(); return uris }    // Flush / 마무리

        /** Save current part to gallery as JPEG. / 현재 조각을 갤러리에 JPEG로 저장 */
        private fun save() {
            val p = part ?: return                                          // Nothing / 없음
            val img = if (used == PART_HEIGHT) p else Bitmap.createBitmap(p, 0, 0, width, used) // Trim / 잘라내기
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "longshot_${stamp}_${uris.size + 1}.jpg") // Name / 이름
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")                                // Type / 형식
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SalaryTicker")                 // Folder / 폴더
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return // Create / 생성
            contentResolver.openOutputStream(uri)?.use { img.compress(Bitmap.CompressFormat.JPEG, 92, it) } // Write / 쓰기
            uris += uri.toString()                                          // Remember / 기록
            if (img !== p) img.recycle(); p.recycle(); part = null; used = 0 // Free / 해제
        }
    }

    private companion object {
        const val MAX_STEPS = 300            // Safety cap on scroll steps / 스크롤 횟수 상한
        const val WIGGLES = 24               // Wiggle segments ≈ 40% of path time / 흔들기 구간 수 (경로 시간의 약 40%)
        const val PART_HEIGHT = 8_000        // Rows per saved image / 이미지 1장 최대 높이
        const val MAX_TOTAL_HEIGHT = 120_000 // Total limit (infinite feeds) / 전체 한도 (무한 스크롤 대비)
    }
}
