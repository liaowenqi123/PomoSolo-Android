package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.P2PTestStore
import com.pomogrow.pomosolo.data.P2PTransfer
import com.pomogrow.pomosolo.data.StudyRoomStore
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoGreen
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim
import kotlinx.coroutines.delay

/**
 * P2P 打洞测试页（与桌面端 `P2PTestPanel.vue` 互通）。
 *
 * 点在线用户运行 A（本机 offerer）/ B（反向）两轮双向测速；对方（桌面端或另一台安卓）
 * 若发起测试，本机会通过 `p2p:test_request` 自动挂起接收并回传结果。
 */
@Composable
fun P2PTestScreen(onBack: () -> Unit) {
    val users by P2PTestStore.users.collectAsState()
    val testing by P2PTestStore.testing.collectAsState()
    val results by P2PTestStore.results.collectAsState()
    val message by P2PTestStore.message.collectAsState()
    val progress by P2PTransfer.progress.collectAsState()

    LaunchedEffect(Unit) {
        // 首次进入时 WS 可能还没握手完成，直接发请求会失败（实测踩过），故轮询等待就绪
        if (!StudyRoomStore.state.value.connected) StudyRoomStore.connect()
        repeat(24) {
            if (StudyRoomStore.state.value.connected) return@repeat
            delay(300)
        }
        P2PTestStore.refreshUsers()
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(6_000)
            P2PTestStore.consumeMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PomoBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = PomoText)
            }
            Text("P2P 打洞测试", color = PomoText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { P2PTestStore.refreshUsers() }, enabled = !testing) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新用户", tint = PomoTextDim)
            }
        }
        Text(
            "与桌面端互通：点在线用户按「本机发起 / 反向」两轮测速；" +
                "对方在桌面端点测试时，本机会自动挂起接收。无需进自习室。",
            color = PomoTextDim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))

        if (progress.active) {
            Text(
                buildString {
                    append("P2P ${progress.role} · ${progress.peerId.take(8)}")
                    append(" · ${progress.transferredBytes / 1024} KB")
                    if (progress.totalBytes > 0) append(" / ${progress.totalBytes / 1024} KB")
                    append(" · %.1f Mbps".format(progress.speedBps / 1024.0 / 1024.0 * 8))
                    append(if (progress.connected) " · 已直连" else " · 打洞中…")
                },
                color = PomoGreen,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "在线用户 (${users.size})",
            color = PomoText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        if (users.isEmpty()) {
            Text("暂无其他在线用户", color = PomoTextDim, fontSize = 12.sp)
        } else {
            users.forEach { u ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PomoSurface)
                        .clickable(enabled = !testing) { P2PTestStore.runFullTest(u) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.username, color = PomoText, fontSize = 14.sp)
                        Text(
                            u.userId,
                            color = PomoTextDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (testing) "测试中…" else "开始测试",
                        color = PomoPrimary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("测试结果", color = PomoText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            results.forEach { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PomoSurface)
                        .padding(12.dp),
                ) {
                    Text(r.name, color = PomoText, fontSize = 13.sp)
                    Text(
                        "↑ %.1f Mbps   ↓ %.1f Mbps   · %s".format(r.upMbps, r.downMbps, r.note),
                        color = PomoTextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.CenterStart) {
            message?.let {
                Text(it, color = PomoPrimary, fontSize = 12.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
