package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.AuthStore
import com.pomogrow.pomosolo.data.P2PTransfer
import com.pomogrow.pomosolo.data.StudyRoomStore
import com.pomogrow.pomosolo.data.StudyRoomState
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoGreen
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoSurfaceHigh
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim
import kotlinx.coroutines.delay

/**
 * 自习室页（对齐桌面端 StudyRoom.vue）：房间列表 / 创建 / 加入 → 房内成员、聊天、同步听歌（DJ）。
 * 通信走 [StudyRoomStore] 的 WebSocket。
 */
@Composable
fun StudyRoomScreen() {
    val state by StudyRoomStore.state.collectAsState()
    val auth by AuthStore.state.collectAsState()
    val p2pMessage by P2PTransfer.message.collectAsState()

    // P2P 结果提示 4 秒后自动清除（避免长期占用提示条）
    LaunchedEffect(p2pMessage) {
        if (p2pMessage != null) {
            delay(4_000)
            P2PTransfer.consumeMessage()
        }
    }

    LaunchedEffect(auth.loggedIn) {
        if (auth.loggedIn) {
            StudyRoomStore.connect()
            StudyRoomStore.loadRooms()
        }
    }

    if (!auth.loggedIn) {
        Column(
            Modifier.fillMaxSize().background(PomoBg).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔒 自习室需要登录", color = PomoText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("请先到「设置 → 账号」登录或注册", color = PomoTextDim, fontSize = 13.sp)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(PomoBg).statusBarsPadding()) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.inRoom) state.roomName.ifBlank { "自习室" } else "自习室",
                    color = PomoText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        state.kicked -> "已被踢下线"
                        state.connected -> if (state.inRoom) "已连接 · ${state.members.size} 人" else "已连接"
                        state.connecting -> "连接中…"
                        else -> "未连接"
                    },
                    color = if (state.connected) PomoGreen else PomoTextDim,
                    fontSize = 12.sp,
                )
            }
            if (state.inRoom) {
                TextButton(onClick = { StudyRoomStore.leaveRoom() }) {
                    Text("离开", color = PomoPrimary, fontSize = 13.sp)
                }
            } else {
                if (state.loadingRooms) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PomoPrimary)
                }
                IconButton(onClick = { StudyRoomStore.loadRooms() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新房间", tint = PomoTextDim)
                }
            }
        }

        // 固定高度提示条（不挤动布局）
        Box(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 18.dp), Alignment.CenterStart) {
            val tip = state.error ?: p2pMessage ?: state.message
            if (tip != null) {
                Text(
                    tip,
                    color = if (state.error != null) PomoPrimary else PomoGreen,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.inRoom) InRoomContent(state) else LobbyContent(state)
    }
}

// ---------------- 大厅（未进房） ----------------

@Composable
private fun LobbyContent(state: StudyRoomState) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var joinId by remember { mutableStateOf("") }
    var joinPassword by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Card {
            Text("创建自习室", color = PomoText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("创建后你成为房主，可在房间里开同步听歌", color = PomoTextDim, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Field("房间名称", name) { name = it }
            Spacer(Modifier.height(8.dp))
            Field("描述（可选）", desc) { desc = it }
            Spacer(Modifier.height(8.dp))
            Field("加入密码（可选，设置后房间变私密）", password) { password = it }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("创建并进入", enabled = !state.connecting) {
                StudyRoomStore.createRoom(name, desc, password)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card {
            Text("通过房间 ID 加入", color = PomoText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Field("房间 ID", joinId) { joinId = it }
            Spacer(Modifier.height(8.dp))
            Field("密码（若需要）", joinPassword) { joinPassword = it }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("加入房间", enabled = joinId.isNotBlank()) {
                StudyRoomStore.joinRoom(joinId, joinPassword)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("公开自习室", color = PomoText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${state.rooms.size} 个", color = PomoTextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (state.rooms.isEmpty()) {
            Text(
                if (state.loadingRooms) "正在加载…" else "暂无公开房间，先创建一个吧",
                color = PomoTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            state.rooms.forEach { room ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PomoSurface)
                        .clickable { StudyRoomStore.joinRoom(room.id, "") }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            room.name,
                            color = PomoText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append("${room.memberCount} 人")
                                if (room.hasPassword) append(" · 🔒 需要密码")
                                if (room.creatorName.isNotBlank()) append(" · 房主 ${room.creatorName}")
                            },
                            color = PomoTextDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("加入", color = PomoPrimary, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------- 房内 ----------------

@Composable
private fun InRoomContent(state: StudyRoomState) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val me = AuthStore.state.collectAsState().value.user?.id.orEmpty()
    val amDj = state.djUserId.isNotEmpty() && state.djUserId == me
    val p2p by P2PTransfer.progress.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // 成员（横向）
        Text(
            "👥 在线成员 (${state.members.size})",
            color = PomoTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (state.members.isEmpty()) {
            Text(
                "正在同步成员列表…",
                color = PomoTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        } else {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.members, key = { it.userId }) { m ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PomoSurface)
                            // 点其他成员 → 发起 P2P 直连测试（对齐桌面端 P2P 测试工具）
                            .clickable(enabled = m.userId.isNotEmpty() && m.userId != me) {
                                P2PTransfer.startSpeedTest(m.userId)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (m.online) PomoGreen else PomoTextDim),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            m.username + if (m.userId == state.djUserId) " 🎧" else "",
                            color = PomoText,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 同步听歌
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("同步听歌", color = PomoText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        amDj -> "你是 DJ · 你的播放会同步给全房间"
                        state.djUsername.isNotBlank() -> "DJ：${state.djUsername}"
                        else -> "暂无 DJ · 申请后即可由你选歌"
                    },
                    color = PomoTextDim,
                    fontSize = 11.sp,
                )
            }
            if (!amDj) {
                TextButton(onClick = { StudyRoomStore.requestDj() }) {
                    Text("申请当 DJ", color = PomoPrimary, fontSize = 12.sp)
                }
            }
        }

        // P2P 直连状态（点上方成员 → 发起 WebRTC 直连测速）
        if (p2p.active || p2p.connected) {
            Text(
                buildString {
                    append("P2P ${p2p.role}")
                    append(" · ${p2p.transferredBytes / 1024} KB")
                    if (p2p.totalBytes > 0) append(" / ${p2p.totalBytes / 1024} KB")
                    append(" · %.1f Mbps".format(p2p.speedBps / 1024.0 / 1024.0 * 8))
                    append(if (p2p.connected) " · 已直连" else " · 打洞中…")
                },
                color = PomoGreen,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // 聊天
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(state.chat) { c ->
                val mine = c.userId == me
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mine) PomoPrimary else PomoSurface)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        if (!mine) {
                            Text(c.username, color = PomoTextDim, fontSize = 10.sp)
                        }
                        Text(
                            c.message,
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        LaunchedEffect(state.chat.size) {
            if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.size - 1)
        }

        // 输入行
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("说点什么…", color = PomoTextDim, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PomoText,
                    unfocusedTextColor = PomoText,
                    focusedBorderColor = PomoPrimary,
                    unfocusedBorderColor = PomoSurfaceHigh,
                    cursorColor = PomoPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    StudyRoomStore.sendChat(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PomoPrimary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("发送", fontSize = 13.sp)
            }
        }
    }
}

// ---------------- 组件 ----------------

@Composable
private fun Card(content: @Composable () -> Unit) {
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
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(label, color = PomoTextDim, fontSize = 12.sp) },
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

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) PomoPrimary else PomoSurfaceHigh)
            .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
