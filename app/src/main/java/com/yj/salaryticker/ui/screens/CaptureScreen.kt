package com.yj.salaryticker.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yj.salaryticker.capture.CapturePrefs
import com.yj.salaryticker.capture.CaptureService
import com.yj.salaryticker.ui.theme.Lavender

/**
 * Long-screenshot setup and results.
 * 긴 캡처 화면: 켜는 법, 모드 선택, 최근 결과.
 *
 * @param modifier 레이아웃 수정자
 */
@Composable
fun CaptureScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current                                                // Context / 컨텍스트
    val prefs = remember { CapturePrefs(ctx) }                                    // Settings / 설정
    var enabled by remember { mutableStateOf(isServiceOn(ctx)) }                  // Service on? / 서비스 켜짐?
    var fast by remember { mutableStateOf(prefs.fastMode) }                       // Mode / 모드
    var showBtn by remember { mutableStateOf(prefs.showButton) }                  // Button / 버튼
    var refresh by remember { mutableIntStateOf(0) }                              // Recent reload key / 최근 목록 갱신 키

    // Re-check when returning from Settings / 설정 갔다 오면 다시 확인
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { enabled = isServiceOn(ctx); refresh++ } }
        owner.lifecycle.addObserver(obs)                                          // Watch / 감시
        onDispose { owner.lifecycle.removeObserver(obs) }                         // Unwatch / 해제
    }
    val recent = remember(refresh) { prefs.recent }                               // Recent list / 최근 목록

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("긴 화면 캡처", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) // Title / 제목

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {                      // Android 11+ only / 11 이상만
            Text("안드로이드 11 이상에서만 쓸 수 있어요", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        // Step 1: enable service / 1단계: 서비스 켜기
        Column(Modifier.fillMaxWidth().background(Lavender, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (enabled) "✅ 캡처 기능 켜짐" else "1. 캡처 기능 켜기", fontWeight = FontWeight.Bold)
            if (!enabled) {
                Text("설정 › 접근성 › 설치된 앱 › 「초당월급 긴 캡처」를 켜줘.\n" +
                    "다른 앱을 대신 스크롤하고 화면을 찍으려면 이 권한이 필요해. 캡처는 갤러리에만 저장돼.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, Modifier.fillMaxWidth()) {
                    Text("접근성 설정 열기")                                          // Open settings / 설정 열기
                }
            }
        }

        // Step 2: how to use / 2단계: 사용법
        Text("사용법", fontWeight = FontWeight.Bold)
        Text("① 캡처할 앱 화면을 열고\n② 떠 있는 📸 버튼을 누르면 끝까지 자동으로 내려가며 캡처\n" +
            "③ 중간에 멈추려면 볼륨 아래 키\n④ 결과는 갤러리 › SalaryTicker 폴더 (너무 길면 여러 장으로 나눠 저장)",
            style = MaterialTheme.typography.bodyMedium)

        // Options / 옵션
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (fast) "⚡ 빠른 모드" else "🐢 안전 모드", fontWeight = FontWeight.SemiBold)
                Text(if (fast) "쉬지 않고 빠르게. 이미지가 늦게 뜨는 앱은 빈칸이 찍힐 수 있어"
                     else "한 번 내릴 때마다 로딩을 기다려. 느리지만 이미지 많은 앱도 정확해",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = fast, onCheckedChange = { fast = it; prefs.fastMode = it }) // Toggle / 토글
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📸 플로팅 버튼 보이기", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = showBtn, onCheckedChange = { showBtn = it; prefs.showButton = it }) // Toggle / 토글
        }

        Text("은행·카드·일부 메신저처럼 캡처를 막아둔 앱은 찍히지 않아요.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        // Recent results / 최근 결과
        if (recent.isNotEmpty()) {
            Text("최근 캡처", fontWeight = FontWeight.Bold)
            recent.forEachIndexed { i, u ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Uri.parse(u).lastPathSegment?.let { "이미지 #$it" } ?: "캡처 ${i + 1}", Modifier.weight(1f))
                    OutlinedButton(onClick = { open(ctx, u) }) { Text("열기") }       // Open / 열기
                    OutlinedButton(onClick = { share(ctx, u) }, Modifier.padding(start = 6.dp)) { Text("공유") } // Share / 공유
                }
            }
        }
    }
}

/**
 * Whether our accessibility service is enabled.
 * 접근성 서비스가 켜져 있는지.
 *
 * @param ctx 컨텍스트
 * @return 켜져 있으면 true
 */
private fun isServiceOn(ctx: Context): Boolean {
    val me = ComponentName(ctx, CaptureService::class.java).flattenToString()                // Our component / 우리 컴포넌트
    val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty() // Enabled list / 켜진 목록
    return list.split(':').any { it.equals(me, ignoreCase = true) }                          // Contains? / 포함?
}

/**
 * Open image in the gallery viewer.
 * 갤러리 뷰어로 열기.
 *
 * @param ctx 컨텍스트
 * @param uri 이미지 URI
 */
private fun open(ctx: Context, uri: String) = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))                                    // View / 보기
}

/**
 * Share image (KakaoTalk etc.).
 * 이미지 공유 (카톡 등).
 *
 * @param ctx 컨텍스트
 * @param uri 이미지 URI
 */
private fun share(ctx: Context, uri: String) = runCatching {
    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)                                     // Share / 공유
    ctx.startActivity(Intent.createChooser(send, "캡처 공유"))
}
