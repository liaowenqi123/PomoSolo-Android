package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.StatsStore
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoSurfaceHigh
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

/** 设置页：计时 / 提醒 / 显示 / 数据 / 关于（对齐 PWA SettingsPanel 中安卓适用的项）。 */
@Composable
fun SettingsScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val todayCount by StatsStore.todayCount.collectAsState()
    val totalMinutes by StatsStore.totalMinutes.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(PomoBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("设置", color = PomoText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("计时 · 提醒 · 数据", color = PomoTextDim, fontSize = 12.sp)

        Spacer(Modifier.height(18.dp))
        Section("计时") {
            StepperRow(
                label = "专注时长",
                hint = "分钟",
                value = settings.workMinutes,
                step = 5,
                range = 5..90,
                onChange = { v -> SettingsStore.update { it.copy(workMinutes = v) } },
            )
            DividerLine()
            StepperRow(
                label = "休息时长",
                hint = "分钟",
                value = settings.breakMinutes,
                step = 1,
                range = 1..30,
                onChange = { v -> SettingsStore.update { it.copy(breakMinutes = v) } },
            )
            DividerLine()
            StepperRow(
                label = "计划轮数",
                hint = "轮",
                value = settings.planRounds,
                step = 1,
                range = 1..12,
                onChange = { v -> SettingsStore.update { it.copy(planRounds = v) } },
            )
            DividerLine()
            SwitchRow(
                label = "自动开始下一阶段",
                hint = "专注结束自动进入休息（反之亦然）",
                checked = settings.autoStartNext,
                onChange = { v -> SettingsStore.update { it.copy(autoStartNext = v) } },
            )
            DividerLine()
            InfoRow("专注模式", "在专注页开关")
        }

        Spacer(Modifier.height(16.dp))
        Section("提醒") {
            SwitchRow(
                label = "完成提示音",
                hint = "阶段结束时播放系统提示音",
                checked = settings.soundEnabled,
                onChange = { v -> SettingsStore.update { it.copy(soundEnabled = v) } },
            )
            DividerLine()
            SwitchRow(
                label = "震动提醒",
                hint = "阶段结束时震动",
                checked = settings.vibrationEnabled,
                onChange = { v -> SettingsStore.update { it.copy(vibrationEnabled = v) } },
            )
        }

        Spacer(Modifier.height(16.dp))
        Section("显示") {
            SwitchRow(
                label = "计时中保持屏幕常亮",
                hint = "原生能力：PWA 在浏览器中无法保证",
                checked = settings.keepScreenOn,
                onChange = { v -> SettingsStore.update { it.copy(keepScreenOn = v) } },
            )
            DividerLine()
            SwitchRow(
                label = "正向计时超过 1 分钟才计入统计",
                hint = "对齐 PWA 文案：💡 超过1分钟才会计入统计",
                checked = settings.stopwatchCountOverOneMinute,
                onChange = { v -> SettingsStore.update { it.copy(stopwatchCountOverOneMinute = v) } },
            )
        }

        Spacer(Modifier.height(16.dp))
        Section("数据") {
            InfoRow("今日完成", "$todayCount 个")
            DividerLine()
            InfoRow("累计专注", "$totalMinutes 分钟")
            DividerLine()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("清空今日统计", color = PomoText, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { StatsStore.resetToday() }) {
                    Text("清空", color = PomoPrimary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Section("关于") {
            InfoRow("应用", "PomoSolo Android")
            DividerLine()
            InfoRow("版本", "V1 · 原生 Kotlin + Compose")
            DividerLine()
            InfoRow("曲库服务", "api.pomogrow.top")
            DividerLine()
            InfoRow("自习室 / 账号", "规划中（对齐 PWA 页面）")
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------- 组件 ----------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = PomoTextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PomoSurface)
                .padding(horizontal = 14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(color = PomoSurfaceHigh, thickness = 1.dp)
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = PomoText, fontSize = 14.sp)
            Text(hint, color = PomoTextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PomoPrimary,
            ),
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    hint: String,
    value: Int,
    step: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = PomoText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        RoundIconBtn("−") { onChange((value - step).coerceIn(range.first, range.last)) }
        Text(
            "$value $hint",
            color = PomoText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundIconBtn("+") { onChange((value + step).coerceIn(range.first, range.last)) }
    }
}

@Composable
private fun RoundIconBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(PomoSurfaceHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = PomoText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = PomoText, fontSize = 14.sp)
        Text(value, color = PomoTextDim, fontSize = 13.sp)
    }
}
