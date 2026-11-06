package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yj.salaryticker.ui.Format
import com.yj.salaryticker.ui.UiState
import com.yj.salaryticker.ui.theme.Gold

/**
 * Main counter: big earned amount + start/stop.
 * 메인 카운터 화면: 큰 누적 금액 + 시작/정지 버튼.
 *
 * @param state 화면 상태
 * @param onStart 시작 클릭
 * @param onStop 정지 클릭
 * @param onReset 초기화 클릭
 * @param onGoSettings 설정 이동
 * @param modifier 레이아웃 수정자
 */
@Composable
fun CounterScreen(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),     // Full screen padding / 전체 여백
        horizontalAlignment = Alignment.CenterHorizontally,   // Center items / 가운데 정렬
        verticalArrangement = Arrangement.Center,             // Vertical center / 세로 가운데
    ) {
        // No salary yet → guide to settings / 월급 미설정 시 설정 유도
        if (state.settings.monthlySalary <= 0L) {
            Text("먼저 월급을 입력해줘", style = MaterialTheme.typography.titleMedium) // Guide text / 안내 문구
            Spacer(Modifier.height(16.dp))                                              // Gap / 간격
            Button(onClick = onGoSettings) { Text("설정하러 가기") }                     // Go settings / 설정 이동
            return@Column                                                                // Stop drawing / 이후 생략
        }

        Text("오늘 번 돈", style = MaterialTheme.typography.titleMedium)                // Label / 라벨
        Spacer(Modifier.height(8.dp))                                                   // Gap / 간격
        Text(
            text = "₩ " + Format.won(state.earnedToday, 2),                            // Earned / 누적액
            fontSize = 44.sp,                                                           // Big number / 큰 숫자
            fontWeight = FontWeight.Bold,                                               // Bold / 굵게
            color = MaterialTheme.colorScheme.primary,                                  // Brand color / 브랜드색
        )
        Spacer(Modifier.height(8.dp))                                                   // Gap / 간격
        Text("초당 " + Format.won(state.ratePerSecond, 2) + "원", color = Gold, fontWeight = FontWeight.SemiBold) // Rate / 초당 급여
        Text("근무 " + Format.hms(state.elapsedMs))                                     // Worked time / 근무 시간

        Spacer(Modifier.height(24.dp))                                                  // Gap / 간격
        val progress = if (state.dailyTarget > 0) (state.earnedToday / state.dailyTarget).toFloat() else 0f // Day progress / 하루 진행률
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },                                   // Clamp 0..1 / 0~1 제한
            modifier = Modifier.fillMaxWidth().height(10.dp),                           // Wide bar / 넓은 바
        )
        Text(
            "하루치 " + Format.won(state.dailyTarget) + "원 중 " + (progress * 100).toInt() + "%", // Progress text / 진행률 문구
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(32.dp))                                                  // Gap / 간격
        val running = state.session.isRunning                                           // Running? / 실행 중?
        Button(
            onClick = if (running) onStop else onStart,                                 // Toggle / 토글
            modifier = Modifier.fillMaxWidth().height(64.dp),                           // Big button / 큰 버튼
            shape = RoundedCornerShape(16.dp),                                          // Rounded / 둥근 모서리
            colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors(),                                         // Red when running / 실행 중엔 빨강
        ) {
            Text(if (running) "일시정지" else "시작", fontSize = 20.sp)                    // Label / 버튼 문구
        }
        TextButton(onClick = onReset) { Text("오늘 기록 초기화") }                         // Reset / 초기화
    }
}
