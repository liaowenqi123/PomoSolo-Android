package com.pomogrow.pomosolo.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pomogrow.pomosolo.data.AuthState
import com.pomogrow.pomosolo.data.AuthStore
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoGreen
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoSurfaceHigh
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

/**
 * 账号页（对齐桌面端 AuthPanel.vue + PWA 的认证实现）：
 * 未登录 = 登录/注册 Tab；已登录 = 用户信息 + 会话操作 + admin 的 DeepSeek Key 下发；
 * 底部固定展示服务器地址与连接测试。
 */
@Composable
fun AuthScreen(onBack: () -> Unit) {
    val state by AuthStore.state.collectAsState()

    var tab by remember { mutableStateOf(0) } // 0 = 登录，1 = 注册
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PomoBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = PomoText,
                )
            }
            Text("账号", color = PomoText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))
        if (state.loggedIn) {
            LoggedInCard(state)
        } else {
            Panel {
                TabRow(tab) { tab = it }
                Spacer(Modifier.height(12.dp))
                AuthField(
                    label = "用户名",
                    value = username,
                    onChange = { username = it },
                    isPassword = false,
                    visible = true,
                )
                Spacer(Modifier.height(8.dp))
                AuthField(
                    label = "密码",
                    value = password,
                    onChange = { password = it },
                    isPassword = true,
                    visible = showPassword,
                )
                if (tab == 1) {
                    Spacer(Modifier.height(8.dp))
                    AuthField(
                        label = "确认密码",
                        value = confirm,
                        onChange = { confirm = it },
                        isPassword = true,
                        visible = showPassword,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "隐藏密码" else "显示密码", color = PomoTextDim, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (tab == 0) {
                            AuthStore.login(username, password)
                        } else {
                            AuthStore.register(username, password, confirm)
                        }
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PomoPrimary,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(if (tab == 0) "登录" else "注册", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                HintBox(state)
            }
        }

        Spacer(Modifier.height(16.dp))
        ServerCard(state)
        Spacer(Modifier.height(16.dp))

        Panel {
            InfoLine("服务器", AuthStore.API_ORIGIN)
            Divider()
            InfoLine("Token", "access 15 分钟 · refresh 30 天滚动刷新")
            Divider()
            InfoLine("凭据存储", "仅保存在本机应用私有目录")
            Divider()
            InfoLine("说明", "登录后 admin 账号会自动同步服务器下发的 DeepSeek Key")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PomoSurface)
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PomoSurfaceHigh)
            .padding(4.dp),
    ) {
        TabPill("登录", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        TabPill("注册", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun RowScope.TabPill(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) PomoPrimary else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else PomoTextDim,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isPassword: Boolean,
    visible: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(label, color = PomoTextDim, fontSize = 13.sp) },
        visualTransformation = if (isPassword && !visible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PomoText,
            unfocusedTextColor = PomoText,
            focusedBorderColor = PomoPrimary,
            unfocusedBorderColor = PomoSurfaceHigh,
            cursorColor = PomoPrimary,
            focusedLabelColor = PomoPrimary,
            unfocusedLabelColor = PomoTextDim,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 提示区：固定高度，避免出现/消失时挤动布局。 */
@Composable
private fun HintBox(state: AuthState) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
        val text = state.error ?: state.message
        if (text != null) {
            Text(
                text,
                color = if (state.error != null) PomoPrimary else PomoGreen,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoggedInCard(state: AuthState) {
    val user = state.user ?: return
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(PomoPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.username.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.nickname.ifBlank { user.username },
                        color = PomoText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.admin) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PomoPrimary)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("ADMIN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "@${user.username}" + if (user.email.isNotBlank()) " · ${user.email}" else "",
                    color = PomoTextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("ID：${user.id}", color = PomoTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedAction("刷新会话", enabled = !state.busy) { AuthStore.refreshSession() }
            OutlinedAction("退出登录", enabled = !state.busy) { AuthStore.logout() }
        }
        if (user.admin) {
            Spacer(Modifier.height(8.dp))
            OutlinedAction("同步 DeepSeek Key（admin）", enabled = !state.busy) {
                AuthStore.syncDeepSeekKeyIfAdmin()
            }
        }
        HintBox(state)
    }
}

@Composable
private fun OutlinedAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .border(1.dp, PomoSurfaceHigh, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (enabled) PomoText else PomoTextDim, fontSize = 12.sp)
    }
}

@Composable
private fun ServerCard(state: AuthState) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("服务器连接", color = PomoText, fontSize = 14.sp)
                Text(
                    when (state.serverOk) {
                        true -> "连接正常 · 延迟 ${state.latencyMs}ms"
                        false -> "无法连接"
                        null -> "未测试"
                    },
                    color = when (state.serverOk) {
                        true -> PomoGreen
                        false -> PomoPrimary
                        null -> PomoTextDim
                    },
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = { AuthStore.testConnection() }, enabled = !state.busy) {
                Icon(Icons.Filled.Refresh, contentDescription = "测试连接", tint = PomoTextDim)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PomoText, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = PomoTextDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PomoSurfaceHigh))
}

/** 供设置页展示的账号摘要文案。 */
fun authSummary(state: AuthState, aiFromServer: Boolean): String = when {
    state.loggedIn && state.user?.admin == true -> "已登录 · admin" + if (aiFromServer) " · Key 由服务器下发" else ""
    state.loggedIn -> "已登录 · ${state.user?.username.orEmpty()}"
    else -> "未登录"
}

/** 便捷入口：设置页读取 AI Key 来源时使用。 */
@Composable
fun rememberAiFromServer(): Boolean {
    val config by AiPickStore.config.collectAsState()
    return config.fromServer
}
