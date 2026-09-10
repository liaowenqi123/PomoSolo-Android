package com.pomogrow.pomosolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.player.NowPlaying
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

/**
 * 全局迷你播放条：悬浮在所有页面之上（对齐 PWA 主计时页底部的音乐播放器）。
 */
@Composable
fun MiniPlayerBar(
    now: NowPlaying,
    playing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PomoSurface)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(PomoPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = Color.White, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("正在播放", color = PomoTextDim, fontSize = 11.sp)
            Text(
                now.title,
                color = PomoText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = PomoText,
            )
        }
    }
}

/** 展开播放面板：进度拖拽 / 上一首 / 播放暂停 / 下一首 / 停止。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    now: NowPlaying,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onStop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playing by PlayerController.playing.collectAsState()
    val pos by PlayerController.positionMs.collectAsState()
    val dur by PlayerController.durationMs.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                now.title,
                color = PomoText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (now.isLocal) "本地文件 · 离线可播" else "在线播放",
                color = PomoTextDim,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(18.dp))

            val progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            Slider(value = progress, onValueChange = { PlayerController.seekTo((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth()) {
                Text(fmtTime(pos), color = PomoTextDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(dur), color = PomoTextDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        tint = PomoText,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(PomoPrimary).clickable { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        tint = PomoText,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onStop) {
                Text("停止并关闭", color = PomoTextDim)
            }
        }
    }
}

internal fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
