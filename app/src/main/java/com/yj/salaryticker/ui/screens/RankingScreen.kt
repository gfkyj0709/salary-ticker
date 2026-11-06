package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yj.salaryticker.ui.Format
import com.yj.salaryticker.ui.RankKind
import com.yj.salaryticker.ui.RankPeriod
import com.yj.salaryticker.ui.RankScope
import com.yj.salaryticker.ui.RankSelection
import com.yj.salaryticker.ui.UiState
import com.yj.salaryticker.ui.theme.Lavender

/**
 * Ranking with scope / period / kind filters.
 * 랭킹 화면: 범위(전체/그룹) · 기간(오늘/주/월) · 종류(월급/야근 손해).
 *
 * @param state 화면 상태
 * @param onSelect 조건 변경 콜백
 * @param modifier 레이아웃 수정자
 */
@Composable
fun RankingScreen(state: UiState, onSelect: ((RankSelection) -> RankSelection) -> Unit, modifier: Modifier = Modifier) {
    val sel = state.selection                                                                  // Current filter / 현재 조건
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (sel.kind == RankKind.PAY) "월급 레이스" else "공짜 야근 손해 랭킹",              // Title / 제목
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        // Kind / 종류
        ChipRow {
            Chip("💰 월급", sel.kind == RankKind.PAY) { onSelect { it.copy(kind = RankKind.PAY) } }
            Chip("😵 야근 손해", sel.kind == RankKind.OVERTIME_LOSS) { onSelect { it.copy(kind = RankKind.OVERTIME_LOSS) } }
        }
        // Period / 기간
        ChipRow {
            Chip("오늘", sel.period == RankPeriod.TODAY) { onSelect { it.copy(period = RankPeriod.TODAY) } }
            Chip("이번 주", sel.period == RankPeriod.WEEK) { onSelect { it.copy(period = RankPeriod.WEEK) } }
            Chip("이번 달", sel.period == RankPeriod.MONTH) { onSelect { it.copy(period = RankPeriod.MONTH) } }
        }
        // Scope / 범위
        ChipRow {
            Chip("전체", sel.scope == RankScope.ALL) { onSelect { it.copy(scope = RankScope.ALL) } }
            Chip("우리 그룹", sel.scope == RankScope.GROUP) { onSelect { it.copy(scope = RankScope.GROUP) } }
        }

        // Empty-group hint / 그룹 미가입 안내
        if (sel.scope == RankScope.GROUP && state.settings.group.isEmpty()) {
            Text("아직 그룹이 없어. 설정에서 코드를 만들거나 친구 코드를 넣어줘", color = Color.Gray)
            return@Column
        }
        Text("🟢 지금 근무·야근 중 · 최근 7일 접속자", style = MaterialTheme.typography.bodySmall, color = Color.Gray) // Legend / 범례

        LazyColumn {
            itemsIndexed(state.ranking, key = { _, r -> r.id }) { i, r ->                    // Stable keys / 안정 키
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(if (r.isMe) Lavender else Color.Transparent, RoundedCornerShape(12.dp)) // Highlight me / 내 줄 강조
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" } // Medal / 메달
                    Text(medal, Modifier.width(40.dp))                                         // Rank / 순위
                    Text(
                        (if (r.active) "🟢 " else "") + r.nickname + if (r.isMe) " (나)" else "", // Name / 이름
                        Modifier.weight(1f), fontWeight = if (r.isMe) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        "₩ " + Format.won(r.amount), fontWeight = FontWeight.SemiBold,            // Amount / 금액
                        color = if (sel.kind == RankKind.OVERTIME_LOSS) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        }
    }
}

/**
 * Horizontal row of chips.
 * 칩 가로 배치.
 *
 * @param content 칩들
 */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() } // Row / 가로줄
}

/**
 * Selectable chip (filled when selected).
 * 선택형 칩 (선택되면 채움).
 *
 * @param text 문구
 * @param selected 선택 여부
 * @param onClick 클릭
 */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 12.dp, vertical = 4.dp)                               // Compact / 작게
    val m = Modifier.height(36.dp)                                                             // Height / 높이
    if (selected) Button(onClick = onClick, modifier = m, contentPadding = pad) { Text(text) }  // Selected / 선택
    else OutlinedButton(onClick = onClick, modifier = m, contentPadding = pad) { Text(text) }   // Not / 미선택
}
