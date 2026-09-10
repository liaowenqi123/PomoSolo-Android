package com.pomogrow.pomosolo.ui

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.PomodoroSettings
import com.pomogrow.pomosolo.data.PomodoroTimer
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.TimerEvent
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

private const val TAB_FOCUS = 0
private const val TAB_MUSIC = 1
private const val TAB_SETTINGS = 2

/**
 * V1 应用骨架（对齐 PWA 可见页面）：
 * 专注（番茄钟主计时页）/ 音乐（曲库下载 + 本地管理）/ 设置，
 * 全局迷你播放器悬浮在底部导航之上。
 */
@Composable
fun PomodoroApp() {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(TAB_FOCUS) }
    var showPlayer by remember { mutableStateOf(false) }

    val now by PlayerController.now.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    val error by PlayerController.error.collectAsState()

    // 播放错误可见化（原先静默 → 用户只看到“没声音”）
    LaunchedEffect(error) {
        val e = error
        if (e != null) {
            snackbar.showSnackbar(e)
            PlayerController.consumeError()
        }
    }

    // 阶段完成提醒：提示音 + 震动 + 文案
    LaunchedEffect(Unit) {
        PomodoroTimer.events.collect { event ->
            val settings = SettingsStore.settings.value
            val text = when (event) {
                TimerEvent.WORK_DONE -> "专注完成，休息一下 ☕"
                TimerEvent.BREAK_DONE -> "休息结束，继续专注 💼"
                TimerEvent.PLAN_DONE -> "计划全部完成 🎉"
            }
            notifyFinish(context, settings)
            snackbar.showSnackbar(text)
        }
    }

    Box(Modifier.fillMaxSize().background(PomoBg)) {
        Scaffold(
            containerColor = PomoBg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { PomoBottomBar(tab) { tab = it } },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    TAB_FOCUS -> FocusScreen()
                    TAB_MUSIC -> MusicPage()
                    else -> SettingsScreen()
                }
            }
        }

        val current = now
        if (current != null) {
            MiniPlayerBar(
                now = current,
                playing = playing,
                onToggle = { PlayerController.toggle() },
                onOpen = { showPlayer = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp),
            )
        }
    }

    val current = now
    if (showPlayer && current != null) {
        PlayerSheet(
            now = current,
            onDismiss = { showPlayer = false },
            onToggle = { PlayerController.toggle() },
            onNext = { PlayerController.next() },
            onPrev = { PlayerController.prev() },
            onStop = {
                PlayerController.stop()
                showPlayer = false
            },
        )
    }
}

@Composable
private fun PomoBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = PomoSurface) {
        BottomItem(TAB_FOCUS, selected, onSelect, "专注", Icons.Filled.Timer)
        BottomItem(TAB_MUSIC, selected, onSelect, "音乐", Icons.Filled.LibraryMusic)
        BottomItem(TAB_SETTINGS, selected, onSelect, "设置", Icons.Filled.Settings)
    }
}

@Composable
private fun RowScope.BottomItem(
    index: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: String,
    icon: ImageVector,
) {
    NavigationBarItem(
        selected = selected == index,
        onClick = { onSelect(index) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 12.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = PomoPrimary,
            selectedTextColor = PomoPrimary,
            indicatorColor = PomoSurface,
            unselectedIconColor = PomoTextDim,
            unselectedTextColor = PomoTextDim,
        ),
    )
}

/** 阶段完成提醒：震动 + 系统提示音（原生能力）。 */
private fun notifyFinish(context: Context, settings: PomodoroSettings) {
    if (settings.vibrationEnabled) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
            // 设备不支持震动时忽略
        }
    }
    if (settings.soundEnabled) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {
            // 无提示音资源时忽略
        }
    }
}
