package com.yj.salaryticker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors / 브랜드 색상
val Purple = Color(0xFF7452C0)   // Primary / 메인 보라
val Navy = Color(0xFF303B4A)     // Text / 본문 네이비
val Lavender = Color(0xFFECEEF4) // Surface tint / 연보라 배경
val Gold = Color(0xFFFFB300)     // Money accent / 금액 강조

/**
 * App theme wrapper.
 * 앱 테마 래퍼.
 *
 * @param content 하위 컴포저블
 */
@Composable
fun SalaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,            // Buttons, bar / 버튼, 바
            secondaryContainer = Lavender, // Nav indicator / 탭 표시
            onSurface = Navy,            // Text / 글자
        ),
        content = content,               // Children / 하위 화면
    )
}
