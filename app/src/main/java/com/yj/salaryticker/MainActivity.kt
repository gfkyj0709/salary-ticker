package com.yj.salaryticker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yj.salaryticker.ui.MainViewModel
import com.yj.salaryticker.ui.screens.CounterScreen
import com.yj.salaryticker.ui.screens.RankingScreen
import com.yj.salaryticker.ui.screens.SettingsScreen
import com.yj.salaryticker.ui.theme.SalaryTheme

/**
 * Single activity hosting 3 tabs (counter / ranking / settings).
 * 탭 3개(카운터/랭킹/설정)를 담는 단일 액티비티.
 */
class MainActivity : ComponentActivity() {

    /**
     * Set Compose content.
     * 컴포즈 화면 세팅.
     *
     * @param savedInstanceState 복원 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Parent init / 부모 초기화
        setContent {
            SalaryTheme {
                val vm: MainViewModel = viewModel()                          // Shared VM / 공유 뷰모델
                val state by vm.uiState.collectAsStateWithLifecycle()        // Observe state / 상태 구독
                var tab by rememberSaveable { mutableIntStateOf(0) }         // Selected tab / 선택 탭

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("카운터") }) // Counter tab / 카운터 탭
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Star, null) }, label = { Text("랭킹") })   // Ranking tab / 랭킹 탭
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("설정") }) // Settings tab / 설정 탭
                        }
                    }
                ) { pad ->
                    val m = Modifier.padding(pad) // Respect bars / 바 영역 피하기
                    when (tab) {
                        0 -> CounterScreen(state, { vm.toggleLupin() }, { vm.resetLupinToday() }, { vm.toggleOvertime() }, { vm.resetOvertimeToday() }, { tab = 2 }, m) // Counter / 카운터
                        1 -> RankingScreen(state, { vm.select(it) }, m)                                                  // Ranking / 랭킹
                        else -> SettingsScreen(state.settings, { vm.saveSettings(it); tab = 0 }, vm::newGroupCode, m)    // Settings / 설정
                    }
                }
            }
        }
    }
}
