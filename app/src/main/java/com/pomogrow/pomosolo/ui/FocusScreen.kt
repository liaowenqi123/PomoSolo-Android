package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
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
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

/**
 * 专注页（对齐 PWA 主计时页）：
 * 模式滑块（单次 / 计划 / 正向）→ 专注/休息切换 → 计时圆环 → 开始/重置 → 状态文案 → 今日统计。
 */
@Composable
fun FocusScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val state by PomodoroTimer.state.collectAsState()
    val todayCount by StatsStore.todayCount.collectAsState()
    val totalMinutes by StatsStore.totalMinutes.collectAsState()

    LaunchedEffect(settings) { PomodoroTimer.applySettings(settings) }

    // 计时中保持屏幕常亮（原生能力：PWA 做不到）
    val view = LocalView.current
    val keepOn = settings.keepScreenOn && state.phase == TimerPhase.RUNNING
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val running = state.phase == TimerPhase.RUNNING
    // 复刻 PWA：专注阶段运行中禁止暂停/重置（惩罚机制），可在设置里放开
    val locked = state.mode == TimerMode.WORK && running && !settings.allowPauseDuringWork

    Column(
        Modifier
            .fillMaxSize()
            .background(PomoBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🍅 PomoSolo", color = PomoText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "今日 $todayCount 个 · $totalMinutes 分钟",
                color = PomoTextDim,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(18.dp))
        ModeSlider(state.appMode) { PomodoroTimer.setAppMode(it) }

        Spacer(Modifier.height(12.dp))
        when (state.appMode) {
            AppMode.SINGLE -> ModeSwitch(state.mode) { PomodoroTimer.setMode(it) }
            AppMode.PLAN -> Text(
                "第 ${state.planRound} / ${settings.planRounds} 轮 · 每轮 ${settings.workMinutes} 分钟专注",
                color = PomoTextDim,
                fontSize = 13.sp,
            )
            AppMode.STOPWATCH -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("从零开始累计", color = PomoText, fontSize = 13.sp)
                Text("适合不确定时长的任务", color = PomoTextDim, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(26.dp))
        TimerRing(progress = state.progress, modifier = Modifier.size(260.dp)) {
            Text(
                fmtClock(state.displayMs),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            statusText(state.appMode, state.mode, running),
            color = if (running) PomoPrimary else PomoTextDim,
            fontSize = 14.sp,
            fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { PomodoroTimer.toggle() },
                enabled = !locked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PomoPrimary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (running) "暂停" else "开始", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { PomodoroTimer.reset() },
                enabled = !locked,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (state.appMode == AppMode.STOPWATCH) "结束记录" else "重置",
                    color = if (locked) PomoTextDim else PomoText,
                    fontSize = 15.sp,
                )
            }
        }

        if (locked) {
            Spacer(Modifier.height(8.dp))
            Text("专注中不可暂停 · 可在设置里放开", color = PomoTextDim, fontSize = 11.sp)
        }

        if (state.appMode != AppMode.STOPWATCH && state.phase != TimerPhase.READY) {
            TextButton(onClick = { PomodoroTimer.skip() }) {
                Text("跳过当前阶段", color = PomoTextDim, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日完成", "$todayCount 个", Modifier.weight(1f))
            StatCard("累计专注", "$totalMinutes 分钟", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun statusText(appMode: AppMode, mode: TimerMode, running: Boolean): String = when {
    running && mode == TimerMode.WORK -> "专注中..."
    running && mode == TimerMode.BREAK -> "休息中..."
    !running && appMode == AppMode.PLAN -> "准备开始计划"
    else -> "准备开始专注工作"
}

// ---------------- 组件 ----------------

@Composable
private fun ModeSlider(current: AppMode, onSelect: (AppMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .padding(4.dp),
    ) {
        AppMode.values().forEach { mode ->
            PillTab(mode.label, current == mode, Modifier.weight(1f)) { onSelect(mode) }
        }
    }
}

@Composable
private fun ModeSwitch(current: TimerMode, onSelect: (TimerMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwitchPill("💼 专注", current == TimerMode.WORK) { onSelect(TimerMode.WORK) }
        SwitchPill("☕ 休息", current == TimerMode.BREAK) { onSelect(TimerMode.BREAK) }
    }
}

@Composable
private fun SwitchPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) PomoPrimary else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else PomoTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
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
            .background(if (active) PomoPrimary else Color.Transparent)
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
            color = if (active) Color.White else PomoTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 计时圆环：对齐 PWA TimerProgress（背景 12% 白、进度 85% 白、线宽 5、从顶部顺时针）。 */
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
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.001f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(d, d),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = PomoText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = PomoTextDim, fontSize = 12.sp)
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
