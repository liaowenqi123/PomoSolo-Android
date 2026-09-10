package com.pomogrow.pomosolo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// PomoSolo 品牌色（与桌面/PWA 端一致）
val PomoBg = Color(0xFF141414)
val PomoSurface = Color(0xFF1F1F1F)
val PomoSurfaceHigh = Color(0xFF2A2A2A)
val PomoPrimary = Color(0xFFE94560)
val PomoPrimaryDark = Color(0xFFB53248)
val PomoText = Color(0xFFFFFFFF)
val PomoTextDim = Color(0xFFB3B3B3)
val PomoGreen = Color(0xFF4CAF50)

// ===== PWA/桌面端容器渐变（135deg）=====
// 色值取自主仓库 src/styles/global.css 的 --container-gradient-* / --break-gradient-*
// 与 App.vue 的 .container.stopwatch-mode（去掉 alpha，安卓端直接作为不透明渐变）。
val PomoGradientWork = listOf(Color(0xFFEA6666), Color(0xFF8C3232))
val PomoGradientBreak = listOf(Color(0xFF5AB48C), Color(0xFF4B76A2))
val PomoGradientStopwatch = listOf(Color(0xFF667EEA), Color(0xFF764BA2))

private val PomoColorScheme = darkColorScheme(
    primary = PomoPrimary,
    onPrimary = Color.White,
    secondary = PomoPrimaryDark,
    background = PomoBg,
    onBackground = PomoText,
    surface = PomoSurface,
    onSurface = PomoText,
    surfaceVariant = PomoSurfaceHigh,
    onSurfaceVariant = PomoTextDim,
    outline = PomoSurfaceHigh,
)

@Composable
fun PomoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PomoColorScheme, content = content)
}
