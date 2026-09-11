package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.AppMode
import com.pomogrow.pomosolo.data.PomodoroTimer
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.StatsStore
import com.pomogrow.pomosolo.data.TimerMode
import com.pomogrow.pomosolo.data.TimerPhase
import com.pomogrow.pomosolo.data.TimerState
import com.pomogrow.pomosolo.ui.theme.PomoGradientBreak
import com.pomogrow.pomosolo.ui.theme.PomoGradientStopwatch
import com.pomogrow.pomosolo.ui.theme.PomoGradientWork

/**
 * 专注页（对齐 PWA 主计时页）：
 * 容器渐变（工作红 / 休息绿蓝 / 正向蓝紫）→ 标题 → 模式滑块 → 专注·休息切换 →
 * 计时圆环 → 专注模式开关 → 开始/重置 → 状态文案 → 统计。
 *
 * 布局约定：所有会随状态出现/消失的文案都放在**固定高度**的容器里，
 * 保证按钮点击后页面不发生位移（PWA 的 .status 也始终占位）。
 */
@Composable
fun FocusScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val state by PomodoroTimer.state.collectAsState()
    val todayCount by StatsStore.todayCount.collectAsState()
    val totalMinutes by StatsStore.totalMinutes.collectAsState()

    LaunchedEffect(settings) { PomodoroTimer.applySettings(settings) }

    // 计时中保持屏幕常亮（原生能力）
    val view = LocalView.current
    val keepOn = settings.keepScreenOn && state.phase == TimerPhase.RUNNING
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val running = state.phase == TimerPhase.RUNNING
    // 只有「专注模式」运行中才禁止暂停/重置（PWA 奖惩机制）
    val locked = state.lockedByFocusMode

    Column(
        Modifier
            .fillMaxSize()
            .background(timerBackground(state))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text("🍅 番茄钟", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(14.dp))
        ModeSlider(state.appMode) { PomodoroTimer.setAppMode(it) }

        // 模式说明区：固定高度，避免切换模式时上方内容跳动
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
            when (state.appMode) {
                AppMode.SINGLE -> ModeSwitch(state.mode) { PomodoroTimer.setMode(it) }
                AppMode.PLAN -> Text(
                    "第 ${state.planRound} / ${settings.planRounds} 轮 · 每轮 ${settings.workMinutes} 分钟专注",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
                AppMode.STOPWATCH -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("从零开始累计", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "💡 超过1分钟才会计入统计",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TimerRing(progress = state.progress, modifier = Modifier.size(236.dp)) {
            Text(
                fmtClock(state.displayMs),
                color = Color.White,
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.30f),
                        offset = Offset(0f, 4f),
                        blurRadius = 20f,
                    ),
                ),
            )
        }

        // 专注模式开关：固定高度（正向模式不显示时也占位）
        Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
            if (state.appMode != AppMode.STOPWATCH) {
                FocusModeSwitch(
                    active = state.focusMode,
                    enabled = state.phase == TimerPhase.READY,
                    onToggle = { PomodoroTimer.setFocusMode(!state.focusMode) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PomoButton(
                text = if (running) "暂停" else "开始",
                primary = true,
                enabled = !locked,
                onClick = { PomodoroTimer.toggle() },
            )
            PomoButton(
                text = "重置",
                primary = false,
                enabled = !locked,
                onClick = { PomodoroTimer.reset() },
            )
        }

        Spacer(Modifier.height(10.dp))
        // 状态文案：固定高度
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            Text(
                statusText(state),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }

        // 辅助行（专注模式提示 / 跳过阶段）：固定高度，出现或消失都不改变布局
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            when {
                state.appMode == AppMode.PLAN && state.phase != TimerPhase.READY -> TextButton(
                    onClick = { PomodoroTimer.skip() },
                ) {
                    Text("跳过当前阶段", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                locked -> Text(
                    "专注模式：运行中不可暂停",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
                state.focusMode -> Text(
                    "专注模式已开启 · 重置将中断本轮专注",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日完成", "$todayCount 个", Modifier.weight(1f))
            StatCard("累计专注", "$totalMinutes 分钟", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 容器渐变：与 PWA 的 .container / .break-mode / .stopwatch-mode 一致。 */
private fun timerBackground(state: TimerState): Brush {
    val stops = when {
        state.appMode == AppMode.STOPWATCH -> PomoGradientStopwatch
        state.mode == TimerMode.BREAK -> PomoGradientBreak
        else -> PomoGradientWork
    }
    return Brush.linearGradient(stops)
}

private fun statusText(state: TimerState): String = when {
    state.phase == TimerPhase.RUNNING && state.mode == TimerMode.WORK -> "专注中..."
    state.phase == TimerPhase.RUNNING && state.mode == TimerMode.BREAK -> "休息中..."
    state.appMode == AppMode.PLAN -> "准备开始计划"
    else -> "准备开始专注工作"
}

// ---------------- 组件 ----------------

/** 模式滑块（单次 / 计划 / 正向）：半透明白底 + 白色高亮（PWA 拨杆同色系）。 */
@Composable
private fun ModeSlider(current: AppMode, onSelect: (AppMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(4.dp),
    ) {
        AppMode.values().forEach { mode ->
            PillTab(mode.label, current == mode, Modifier.weight(1f)) { onSelect(mode) }
        }
    }
}

/** 专注 / 休息切换（PWA .mode-btn：半透明白胶囊 + emoji）。 */
@Composable
private fun ModeSwitch(current: TimerMode, onSelect: (TimerMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModePill("💼 专注", current == TimerMode.WORK) { onSelect(TimerMode.WORK) }
        ModePill("☕ 休息", current == TimerMode.BREAK) { onSelect(TimerMode.BREAK) }
    }
}

@Composable
private fun ModePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (active) 0.25f else 0.15f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (active) 0.5f else 0.3f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RowScope.PillTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color.White.copy(alpha = 0.94f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color(0xFFB53248) else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 专注模式开关（PWA FocusModeSwitch）：胶囊轨道 + 滑块，激活时绿色。
 * 仅 READY 阶段可切换，运行中禁用（disbled 时降透明度且不可点）。
 */
@Composable
private fun FocusModeSwitch(active: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "专注模式",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Color(0x994CAF50) else Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = if (active) 23.dp else 3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (active) "开启" else "关闭",
            color = if (active) Color(0xFF81C784) else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

/** 主按钮（PWA .btn-start / .btn-reset）：主按钮白底深字、次按钮描边，宽度统一。 */
@Composable
private fun PomoButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(104.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (primary) {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.86f)),
                    )
                } else {
                    SolidColor(Color.White.copy(alpha = 0.12f))
                },
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (primary) 0f else 0.38f),
                shape = RoundedCornerShape(20.dp),
            )
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color(0xFFB53248) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 计时圆环：对齐 PWA TimerProgress（背景 12% 白、进度 85% 白、线宽 5、顶部顺时针）。 */
@Composable
private fun TimerRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.001f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.92f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(d, d),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // 内圈（对齐 PWA `.timer-inner`：145deg 半透明白渐变 + 细边框 + 内阴影感）
        Box(
            Modifier
                .fillMaxSize(0.82f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)),
                    ),
                )
                .border(2.dp, Color.White.copy(alpha = 0.20f), CircleShape),
        )
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
    }
}

/** mm:ss，超过一小时显示 h:mm:ss。 */
private fun fmtClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
