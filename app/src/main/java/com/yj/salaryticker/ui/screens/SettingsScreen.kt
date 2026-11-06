package com.yj.salaryticker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yj.salaryticker.data.SalarySettings
import com.yj.salaryticker.domain.SalaryCalculator
import com.yj.salaryticker.ui.Format

/**
 * Salary input form.
 * 월급 입력 화면.
 *
 * @param current 현재 저장된 설정
 * @param onSave 저장 클릭 콜백
 * @param modifier 레이아웃 수정자
 */
@Composable
fun SettingsScreen(current: SalarySettings, onSave: (SalarySettings) -> Unit, modifier: Modifier = Modifier) {
    // Local edit state, re-init when saved value changes / 편집용 로컬 상태 (저장값 바뀌면 재초기화)
    var nickname by remember(current) { mutableStateOf(current.nickname) }
    var salary by remember(current) { mutableStateOf(if (current.monthlySalary > 0) current.monthlySalary.toString() else "") }
    var days by remember(current) { mutableStateOf(current.workDaysPerMonth.toString()) }
    var hours by remember(current) { mutableStateOf(current.workHoursPerDay.toString()) }

    // Parse inputs safely (invalid → defaults) / 입력값 안전 파싱 (잘못되면 기본값)
    val draft = SalarySettings(
        nickname = nickname.trim().ifEmpty { "나" },       // Nickname / 닉네임
        monthlySalary = salary.toLongOrNull() ?: 0L,        // Salary / 월급
        workDaysPerMonth = days.toIntOrNull() ?: 21,        // Days / 근무일
        workHoursPerDay = hours.toDoubleOrNull() ?: 8.0,    // Hours / 근무시간
    )

    Column(
        modifier.fillMaxSize().padding(24.dp),             // Padding / 여백
        verticalArrangement = Arrangement.spacedBy(12.dp), // Field gap / 필드 간격
    ) {
        Text("급여 설정", style = MaterialTheme.typography.headlineSmall) // Title / 제목
        OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임 (랭킹 표시)") }, singleLine = true)
        OutlinedTextField(
            salary, { salary = it.filter(Char::isDigit) },                        // Digits only / 숫자만
            Modifier.fillMaxWidth(), label = { Text("세전 월급 (원)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // Number pad / 숫자 키패드
        )
        OutlinedTextField(
            days, { days = it.filter(Char::isDigit) },                            // Digits only / 숫자만
            Modifier.fillMaxWidth(), label = { Text("월 근무일") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            hours, { hours = it },                                                // Allow decimal / 소수 허용
            Modifier.fillMaxWidth(), label = { Text("하루 근무시간 (휴게 제외)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        // Live preview of rate / 초당 급여 미리보기
        Text("→ 초당 " + Format.won(SalaryCalculator.ratePerSecond(draft), 2) + "원", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth()) { Text("저장") } // Save / 저장
    }
}
