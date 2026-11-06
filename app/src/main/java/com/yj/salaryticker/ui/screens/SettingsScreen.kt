package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yj.salaryticker.data.PayType
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.domain.SalaryCalculator
import com.yj.salaryticker.domain.WorkCalendar
import com.yj.salaryticker.ui.Format
import java.time.LocalDate

/**
 * Salary input form (annual or monthly; workdays auto).
 * 급여 입력 화면. 연봉/월급 선택, 근무일은 자동 계산.
 *
 * @param current 현재 저장된 설정
 * @param onSave 저장 클릭 콜백
 * @param modifier 레이아웃 수정자
 */
@Composable
fun SettingsScreen(current: SalarySettings, onSave: (SalarySettings) -> Unit, modifier: Modifier = Modifier) {
    // Local edit state, re-init when saved value changes / 편집용 로컬 상태 (저장값 바뀌면 재초기화)
    var nickname by remember(current) { mutableStateOf(current.nickname) }
    var payType by remember(current) { mutableStateOf(current.payType) }
    var amount by remember(current) { mutableStateOf(if (current.payAmount > 0) current.payAmount.toString() else "") }
    var hours by remember(current) { mutableStateOf(current.workHoursPerDay.toString()) }

    // Parse inputs safely (invalid → defaults) / 입력값 안전 파싱 (잘못되면 기본값)
    val draft = SalarySettings(
        nickname = nickname.trim().ifEmpty { "나" },       // Nickname / 닉네임
        payType = payType,                                  // Type / 종류
        payAmount = amount.toLongOrNull() ?: 0L,            // Amount / 금액
        workHoursPerDay = hours.toDoubleOrNull() ?: 8.0,    // Hours / 근무시간
    )
    val today = LocalDate.now()                             // Today / 오늘
    val workDays = WorkCalendar.workDaysInMonth(today)      // Auto workdays / 자동 근무일

    Column(
        modifier.fillMaxSize().padding(24.dp),             // Padding / 여백
        verticalArrangement = Arrangement.spacedBy(12.dp), // Field gap / 필드 간격
    ) {
        Text("급여 설정", style = MaterialTheme.typography.headlineSmall) // Title / 제목
        OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임 (랭킹 표시)") }, singleLine = true)

        // Annual / monthly toggle / 연봉·월급 선택
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayTypeButton("연봉", payType == PayType.ANNUAL, Modifier.weight(1f)) { payType = PayType.ANNUAL }   // Annual / 연봉
            PayTypeButton("월급", payType == PayType.MONTHLY, Modifier.weight(1f)) { payType = PayType.MONTHLY } // Monthly / 월급
        }
        OutlinedTextField(
            amount, { amount = it.filter(Char::isDigit) },                        // Digits only / 숫자만
            Modifier.fillMaxWidth(),
            label = { Text(if (payType == PayType.ANNUAL) "연봉 (원)" else "월급 (원)") }, // Dynamic label / 라벨 변경
            supportingText = { Text("입력: " + Format.won(draft.payAmount.toDouble()) + "원") }, // Comma echo / 콤마 확인
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // Number pad / 숫자 키패드
        )
        OutlinedTextField(
            hours, { hours = it },                                                // Allow decimal / 소수 허용
            Modifier.fillMaxWidth(), label = { Text("하루 근무시간 (휴게 제외)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        // Auto-calculated summary / 자동 계산 요약
        Text(
            "이번 달 근무일 ${workDays}일 (주말·공휴일 제외, 자동)\n" +          // Auto days / 자동 근무일
                "월 " + Format.won(SalaryCalculator.monthlyPay(draft)) + "원 기준", // Monthly basis / 월 기준
            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
        Text("→ 초당 " + Format.won(SalaryCalculator.ratePerSecond(draft, today), 2) + "원", style = MaterialTheme.typography.titleMedium) // Rate preview / 미리보기
        Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth()) { Text("저장") } // Save / 저장
    }
}

/**
 * Toggle button: filled when selected, outlined otherwise.
 * 선택되면 채워진 버튼, 아니면 테두리 버튼.
 *
 * @param text 버튼 문구
 * @param selected 선택 여부
 * @param modifier 레이아웃 수정자
 * @param onClick 클릭 콜백
 */
@Composable
private fun PayTypeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }      // Selected / 선택됨
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }       // Not selected / 미선택
}
