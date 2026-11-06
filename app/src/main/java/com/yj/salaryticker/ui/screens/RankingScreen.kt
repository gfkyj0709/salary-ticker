package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yj.salaryticker.ui.Format
import com.yj.salaryticker.ui.UiState
import com.yj.salaryticker.ui.theme.Lavender

/**
 * Today's earnings ranking.
 * 오늘 누적액 랭킹 화면.
 *
 * @param state 화면 상태
 * @param modifier 레이아웃 수정자
 */
@Composable
fun RankingScreen(state: UiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("오늘의 월급 레이스", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) // Title / 제목
        Text("오늘 시작 버튼 누른 사람 전체 · 🟢 근무 중", style = MaterialTheme.typography.bodySmall, color = Color.Gray)   // Notice / 안내
        LazyColumn(Modifier.padding(top = 12.dp)) {
            itemsIndexed(state.ranking, key = { _, e -> e.userId }) { i, e ->  // Stable keys / 안정적인 키
                Row(
                    modifier = Modifier
                        .fillMaxWidth()                                          // Full row / 전체 폭
                        .padding(vertical = 4.dp)                                // Row gap / 줄 간격
                        .background(if (e.isMe) Lavender else Color.Transparent, RoundedCornerShape(12.dp)) // Highlight me / 내 줄 강조
                        .padding(12.dp),                                         // Inner padding / 안쪽 여백
                    verticalAlignment = Alignment.CenterVertically,              // Center / 세로 정렬
                ) {
                    val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" } // Medal / 메달
                    Text(medal, Modifier.width(40.dp))                                         // Rank / 순위
                    Text(
                        (if (e.isRunning) "🟢 " else "") + e.nickname + if (e.isMe) " (나)" else "", // Name + status / 이름 + 상태
                        Modifier.weight(1f),                                                   // Fill space / 남은 폭
                        fontWeight = if (e.isMe) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("₩ " + Format.won(e.amount), fontWeight = FontWeight.SemiBold)        // Amount / 금액
                }
            }
        }
    }
}
