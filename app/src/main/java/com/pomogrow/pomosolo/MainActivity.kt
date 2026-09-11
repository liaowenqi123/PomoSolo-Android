package com.pomogrow.pomosolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pomogrow.pomosolo.data.AiPickStore
import com.pomogrow.pomosolo.data.AuthStore
import com.pomogrow.pomosolo.data.MusicStore
import com.pomogrow.pomosolo.data.P2PTransfer
import com.pomogrow.pomosolo.data.PomodoroTimer
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.StatsStore
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.ui.PomodoroApp
import com.pomogrow.pomosolo.ui.theme.PomoTheme

/**
 * V1 主界面：原生 Kotlin + Compose，彻底脱离 WebView / PWA 产物。
 *
 * 页面结构对齐 PWA 可见页面：专注 / 自习室 / 音乐 / 设置（底部导航四个 tab）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 边到边：内容延伸到状态栏下方（渐变/背景铺满），各页面自行处理状态栏内边距
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SettingsStore.init(applicationContext)
        StatsStore.init(applicationContext)
        AiPickStore.init(applicationContext)
        AuthStore.init(applicationContext)
        MusicStore.init(applicationContext)
        PomodoroTimer.init(applicationContext)
        PlayerController.init(applicationContext)
        // P2P 传歌（m5.1 的 WebRTC 传输层）
        P2PTransfer.init(applicationContext)
        setContent {
            PomoTheme {
                PomodoroApp()
            }
        }
    }
}
