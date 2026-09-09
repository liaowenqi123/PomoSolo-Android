package com.pomogrow.pomosolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pomogrow.pomosolo.data.MusicStore
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.ui.MusicApp
import com.pomogrow.pomosolo.ui.theme.PomoTheme

/**
 * V1 主界面：原生 Kotlin + Compose，彻底脱离 WebView / PWA 产物。
 * 当前里程碑核心 = 曲库下载：服务器曲库(在线 manifest) / 内置曲(assets) →
 * 原生落盘(私有目录) → 离线可播 + 本地导入管理。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicStore.init(applicationContext)
        PlayerController.init(applicationContext)
        setContent {
            PomoTheme {
                MusicApp()
            }
        }
    }
}
