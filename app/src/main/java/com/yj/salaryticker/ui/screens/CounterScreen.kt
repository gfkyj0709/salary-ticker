package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yj.salaryticker.domain.WorkStatus
import com.yj.salaryticker.ui.Format
import com.yj.salaryticker.ui.UiState
import com.yj.salaryticker.ui.theme.Gold
import com.yj.salaryticker.ui.theme.Lavender

/**
 * Counter: auto regular pay + overtime-loss card.
 * 카운터 화면: 시간표 기반 자동 수입 + 야근 손해 카드.
 *
 * @param state 화면 상태
 * @param onToggleOvertime 야근 시작/종료
 * @param onResetOvertime 오늘 야근 초기화
 * @param onGoSettings 설정 이동
 * @param modifier 레이아웃 수정자
 */
@Composable
fun CounterScreen(
    state: UiState,
    onToggleOvertime: () -> Unit,
    onResetOvertime: () -> Unit,
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), // Scrollable / 스크롤
        horizontalAlignment = Alignment.CenterHorizontally,                                     // Center / 가운데
    ) {
        // Not configured → guide / 미설정 안내
        if (state.settings.payAmount <= 0L) {
            Spacer(Modifier.height(120.dp))                                                     // Push down / 아래로
            Text("먼저 연봉이나 월급을 입력해줘", style = MaterialTheme.typography.titleMedium)  // Guide / 안내
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoSettings) { Text("설정하러 가기") }                             // Go settings / 설정 이동
            return@Column
        }

        Spacer(Modifier.height(32.dp))
        Text(statusText(state), style = MaterialTheme.typography.titleSmall)                    // Status / 상태
        Spacer(Modifier.height(8.dp))
        Text("오늘 번 돈", style = MaterialTheme.typography.titleMedium)                        // Label / 라벨
        Text(
            "₩ " + Format.won(state.earnedToday, 2), fontSize = 44.sp, fontWeight = FontWeight.Bold, // Amount / 금액
            color = MaterialTheme.colorScheme.primary,
        )
        Text("초당 " + Format.won(state.ratePerSecond, 2) + "원", color = Gold, fontWeight = FontWeight.SemiBold) // Rate / 초당
        Text("근무 " + Format.hms(state.workedMs))                                              // Worked / 근무 시간

        Spacer(Modifier.height(20.dp))
        val progress = if (state.dailyTarget > 0) (state.earnedToday / state.dailyTarget).toFloat() else 0f // Progress / 진행률
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp))
        Text(
            "하루치 " + Format.won(state.dailyTarget) + "원 중 " + (progress.coerceIn(0f, 1f) * 100).toInt() + "%",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(28.dp))
        OvertimeCard(state, onToggleOvertime, onResetOvertime)                                  // OT card / 야근 카드
    }
}

/**
 * Status line text.
 * 상태 문구.
 *
 * @param state 화면 상태
 * @return 문구
 */
private fun statusText(state: UiState): String = when (state.status) {
    WorkStatus.WORKING -> "🟢 근무 중 · 퇴근까지 " + Format.hms(state.msUntilClockOut)        // Working / 근무 중
    WorkStatus.LUNCH -> "🍚 점심시간 (돈은 안 쌓여요)"                                       // Lunch / 점심
    WorkStatus.BEFORE -> "☕ 출근 전 · " + Format.hm(state.settings.startMin) + " 출근"        // Before / 출근 전
    WorkStatus.AFTER -> "🏠 퇴근 시간 지남"                                                   // After / 퇴근 후
    WorkStatus.OFF_DAY -> "😴 오늘은 쉬는 날"                                                  // Day off / 휴무
}

/**
 * Overtime loss card with start/stop button.
 * 야근 손해 카드 (시작/종료 버튼 포함). 정규 근무 중엔 버튼 비활성.
 *
 * @param state 화면 상태
 * @param onToggle 시작/종료
 * @param onReset 초기화
 */
@Composable
private fun OvertimeCard(state: UiState, onToggle: () -> Unit, onReset: () -> Unit) {
    val running = state.overtime.isRunning                                                    // Running? / 진행 중?
    val canStart = state.status != WorkStatus.WORKING && state.status != WorkStatus.LUNCH       // Outside hours only / 근무시간 외만
    Column(
        Modifier.fillMaxWidth().background(Lavender, RoundedCornerShape(16.dp)).padding(16.dp), // Card / 카드
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("공짜 야근 손해", fontWeight = FontWeight.Bold)                                     // Title / 제목
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("오늘 야근 " + Format.hms(state.otTodayMs))                                      // OT time / 야근 시간
            Text("₩ " + Format.won(state.otTodayPay), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) // Loss / 손해
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("이번 달 누적", style = MaterialTheme.typography.bodySmall)                      // Month / 월 누적
            Text("₩ " + Format.won(state.otMonthPay), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onToggle, enabled = running || canStart,                                  // Enable rule / 활성 조건
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp),
            colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                     else ButtonDefaults.buttonColors(),                                        // Red when running / 진행 중 빨강
        ) {
            Text(if (running) "야근 끝" else if (canStart) "야근 시작" else "퇴근 후에 누를 수 있어요", fontSize = 17.sp) // Label / 문구
        }
        Text(
            "통상시급 " + Format.won(state.hourly) + "원 × 1.5배, 22~06시는 +0.5배 (법정 기준)\n" +
                "5인 미만 사업장이나 포괄임금제면 실제와 다를 수 있어요",                          // Basis + caveat / 기준·주의
            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) { Text("오늘 야근 기록 지우기") } // Reset / 초기화
    }
}
