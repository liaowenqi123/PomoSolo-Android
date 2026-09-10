package com.pomogrow.pomosolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pomogrow.pomosolo.data.MusicStore
import com.pomogrow.pomosolo.data.PomodoroTimer
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.StatsStore
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.ui.PomodoroApp
import com.pomogrow.pomosolo.ui.theme.PomoTheme

/**
 * V1 主界面：原生 Kotlin + Compose，彻底脱离 WebView / PWA 产物。
 *
 * 页面结构对齐 PWA 可见页面：专注（番茄钟主计时页）/ 音乐（曲库下载 + 本地管理）/ 设置。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(applicationContext)
        StatsStore.init(applicationContext)
        MusicStore.init(applicationContext)
        PomodoroTimer.init(applicationContext)
        PlayerController.init(applicationContext)
        setContent {
            PomoTheme {
                PomodoroApp()
            }
        }
    }
}
