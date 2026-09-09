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
