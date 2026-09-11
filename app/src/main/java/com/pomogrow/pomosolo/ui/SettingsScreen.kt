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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.AiPickStore
import com.pomogrow.pomosolo.data.AuthStore
import com.pomogrow.pomosolo.data.DeepSeekClient
import com.pomogrow.pomosolo.data.SettingsStore
import com.pomogrow.pomosolo.data.StatsStore
import kotlinx.coroutines.launch
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
    val aiConfig by AiPickStore.config.collectAsState()
    val authState by AuthStore.state.collectAsState()
    var showAuth by remember { mutableStateOf(false) }

    // 账号页：设置内的二级页面
    if (showAuth) {
        AuthScreen(onBack = { showAuth = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PomoBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("设置", color = PomoText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("账号 · 计时 · 提醒 · 数据", color = PomoTextDim, fontSize = 12.sp)

        Spacer(Modifier.height(14.dp))
        // 账号入口（对齐桌面端 AuthPanel 的入口）
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PomoSurface)
                .clickable { showAuth = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (authState.loggedIn) PomoPrimary else PomoSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (authState.loggedIn) {
                        authState.user?.username?.take(1)?.uppercase().orEmpty()
                    } else {
                        "👤"
                    },
                    color = if (authState.loggedIn) Color.White else PomoTextDim,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("账号", color = PomoText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(authSummary(authState, aiConfig.fromServer), color = PomoTextDim, fontSize = 12.sp)
            }
            Text("›", color = PomoTextDim, fontSize = 20.sp)
        }

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
        Section("音乐 · AI 选片") {
            SwitchRow(
                label = "AI 选片（DeepSeek）",
                hint = "下载热榜歌曲时，用大模型从搜索结果里挑出最像纯音乐/原版的版本",
                checked = aiConfig.enabled,
                onChange = { v -> AiPickStore.update { it.copy(enabled = v) } },
            )
            DividerLine()
            ApiKeyBlock(
                value = aiConfig.apiKey,
                onChange = { v -> AiPickStore.update { it.copy(apiKey = v.trim()) } },
            )
            DividerLine()
            InfoRow(
                "Key 状态",
                when {
                    aiConfig.configured -> "已配置"
                    aiConfig.enabled -> "未配置（下载会提示先配置）"
                    else -> "未启用（使用本地规则选片）"
                },
            )
            DividerLine()
            InfoRow(
                label = "Key 来源",
                value = if (aiConfig.fromServer) {
                    "服务器下发（admin 账号）"
                } else {
                    "本机填写 / 登录 admin 可同步"
                },
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
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
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

/** DeepSeek API Key 配置 + 连接测试（对齐桌面端本地配置 Key 的方式）。 */
@Composable
private fun ApiKeyBlock(value: String, onChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("DeepSeek API Key", color = PomoText, fontSize = 14.sp)
        Text(
            "在 platform.deepseek.com 创建；仅保存在本机，不会上传",
            color = PomoTextDim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text("sk-...", color = PomoTextDim, fontSize = 13.sp) },
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "隐藏" else "显示", color = PomoTextDim, fontSize = 12.sp)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PomoText,
                unfocusedTextColor = PomoText,
                focusedBorderColor = PomoPrimary,
                unfocusedBorderColor = PomoSurfaceHigh,
                cursorColor = PomoPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    testing = true
                    result = null
                    val key = value.trim()
                    scope.launch {
                        result = try {
                            val reply = DeepSeekClient.test(key)
                            "连接正常（模型回复：$reply）"
                        } catch (e: Exception) {
                            e.message ?: "测试失败"
                        }
                        testing = false
                    }
                },
                enabled = !testing && value.isNotBlank(),
            ) {
                Text(if (testing) "测试中…" else "测试连接", color = PomoPrimary)
            }
            if (testing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PomoPrimary)
            }
        }
        // 固定高度：测试结果出现/消失不影响页面其它内容
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            result?.let {
                Text(
                    it,
                    color = PomoTextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
