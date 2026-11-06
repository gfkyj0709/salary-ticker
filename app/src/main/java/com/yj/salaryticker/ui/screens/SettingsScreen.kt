package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yj.salaryticker.data.PayType
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.domain.SalaryCalculator
import com.yj.salaryticker.domain.WorkCalendar
import com.yj.salaryticker.ui.Format
import java.time.LocalDate

/**
 * Settings: pay, commute schedule, friend group.
 * 설정 화면: 급여, 출퇴근 시간표, 친구 그룹.
 *
 * @param current 저장된 설정
 * @param onSave 저장 콜백
 * @param newGroupCode 새 그룹 코드 생성기
 * @param modifier 레이아웃 수정자
 */
@Composable
fun SettingsScreen(
    current: SalarySettings,
    onSave: (SalarySettings) -> Unit,
    newGroupCode: () -> String,
    modifier: Modifier = Modifier,
) {
    // Local edit state / 편집용 로컬 상태
    var nickname by remember(current) { mutableStateOf(current.nickname) }
    var payType by remember(current) { mutableStateOf(current.payType) }
    var amount by remember(current) { mutableStateOf(if (current.payAmount > 0) current.payAmount.toString() else "") }
    var start by remember(current) { mutableStateOf(Format.hm(current.startMin)) }
    var end by remember(current) { mutableStateOf(Format.hm(current.endMin)) }
    var lunchStart by remember(current) { mutableStateOf(Format.hm(current.lunchStartMin)) }
    var lunchMin by remember(current) { mutableStateOf(current.lunchMin.toString()) }
    var group by remember(current) { mutableStateOf(current.group) }
    val clipboard = LocalClipboardManager.current                                    // Clipboard / 클립보드

    // Parse safely: invalid time keeps the saved value / 잘못된 시간은 기존 값 유지
    val draft = SalarySettings(
        nickname = nickname.trim().ifEmpty { "나" },
        payType = payType,
        payAmount = amount.toLongOrNull() ?: 0L,
        startMin = Format.parseHm(start) ?: current.startMin,
        endMin = Format.parseHm(end) ?: current.endMin,
        lunchStartMin = Format.parseHm(lunchStart) ?: current.lunchStartMin,
        lunchMin = (lunchMin.toIntOrNull() ?: current.lunchMin).coerceIn(0, 240),
        group = group.trim().uppercase().take(8),
    )
    val scheduleOk = draft.endMin > draft.startMin                                   // Valid order / 순서 검증
    val today = LocalDate.now(SalaryCalculator.zone)                                 // Today / 오늘

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),  // Scrollable / 스크롤
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("급여", style = MaterialTheme.typography.titleLarge)                    // Section / 구역
        OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("닉네임 (랭킹에 공개돼요, 실명 비추)") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("연봉", payType == PayType.ANNUAL, Modifier.weight(1f)) { payType = PayType.ANNUAL }
            Toggle("월급", payType == PayType.MONTHLY, Modifier.weight(1f)) { payType = PayType.MONTHLY }
        }
        OutlinedTextField(
            amount, { amount = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (payType == PayType.ANNUAL) "연봉 (원)" else "월급 (원)") },
            supportingText = { Text("입력: " + Format.won(draft.payAmount.toDouble()) + "원") },  // Comma echo / 콤마 확인
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        HorizontalDivider()
        Text("출퇴근 (평일 자동, 공휴일 제외)", style = MaterialTheme.typography.titleLarge) // Section / 구역
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("출근", start, Modifier.weight(1f)) { start = it }
            TimeField("퇴근", end, Modifier.weight(1f)) { end = it }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("점심 시작", lunchStart, Modifier.weight(1f)) { lunchStart = it }
            OutlinedTextField(lunchMin, { lunchMin = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                label = { Text("점심 (분)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (!scheduleOk) Text("퇴근이 출근보다 늦어야 해", color = MaterialTheme.colorScheme.error) // Error / 오류
        Text(
            "하루 " + Format.hms(SalaryCalculator.dailyWorkMs(draft)).dropLast(3) + " 근무 · 이번 달 근무일 " +
                WorkCalendar.workDaysInMonth(today) + "일 · 월 " + Format.won(SalaryCalculator.monthlyPay(draft)) + "원",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
        Text("→ 초당 " + Format.won(SalaryCalculator.ratePerSecond(draft, today), 2) + "원 · 통상시급 " +
            Format.won(SalaryCalculator.ordinaryHourly(draft)) + "원", style = MaterialTheme.typography.titleMedium)

        HorizontalDivider()
        Text("친구 그룹", style = MaterialTheme.typography.titleLarge)               // Section / 구역
        OutlinedTextField(group, { group = it.uppercase().filter(Char::isLetterOrDigit).take(8) }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("그룹 코드 (친구한테 받은 코드 입력)") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { group = newGroupCode() }, Modifier.weight(1f)) { Text("새 그룹 만들기") } // Create / 생성
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(group)) }, Modifier.weight(1f),
                enabled = group.isNotEmpty()) { Text("코드 복사") }                              // Copy / 복사
        }
        if (group.isNotEmpty()) TextButton(onClick = { group = "" }) { Text("그룹 나가기") }      // Leave / 탈퇴
        Text("코드를 친구한테 보내고, 친구가 같은 코드를 넣으면 '우리 그룹' 랭킹에 같이 보여요",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth(), enabled = scheduleOk) { Text("저장") } // Save / 저장
    }
}

/**
 * Time input field ("09:00", "0900", "9" all accepted).
 * 시간 입력칸 ("09:00", "0900", "9" 모두 허용).
 *
 * @param label 라벨
 * @param value 값
 * @param modifier 수정자
 * @param onChange 변경 콜백
 */
@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == ':' }.take(5)) }, modifier, singleLine = true, // Digits/colon / 숫자·콜론
        label = { Text(label) }, isError = Format.parseHm(value) == null,                                   // Invalid mark / 잘못 표시
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * Two-state toggle button.
 * 선택형 버튼.
 *
 * @param text 문구
 * @param selected 선택 여부
 * @param modifier 수정자
 * @param onClick 클릭
 */
@Composable
private fun Toggle(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }        // Selected / 선택
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }         // Not / 미선택
}
