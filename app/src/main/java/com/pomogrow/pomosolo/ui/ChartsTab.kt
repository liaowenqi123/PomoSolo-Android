package com.pomogrow.pomosolo.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomogrow.pomosolo.data.ChartSource
import com.pomogrow.pomosolo.data.ChartSong
import com.pomogrow.pomosolo.data.ChartsStore
import com.pomogrow.pomosolo.data.DlStage
import com.pomogrow.pomosolo.data.DlTask
import com.pomogrow.pomosolo.data.SongDownloader
import com.pomogrow.pomosolo.ui.theme.PomoGreen
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoSurfaceHigh
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

/**
 * 热榜页（对齐桌面端 Charts.vue + DownloadDialog）：
 * 榜单只提供「歌名 / 歌手」，点 ⬇ 才去音源（B站）搜索并提取音频存到手机本地。
 *
 * 串行队列：一次下载一首，与桌面端 DownloadDialog 的队列语义一致。
 */
@Composable
fun ChartsTab() {
    val source by ChartsStore.source.collectAsState()
    val songs by ChartsStore.songs.collectAsState()
    val loading by ChartsStore.loading.collectAsState()
    val tasks by SongDownloader.tasks.collectAsState()

    LaunchedEffect(Unit) {
        if (songs.isEmpty()) ChartsStore.load()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PomoSurface)
                    .padding(4.dp),
            ) {
                ChartSource.values().forEach { s ->
                    SourcePill(s.shortLabel, source == s, Modifier.weight(1f)) { ChartsStore.select(s) }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PomoPrimary)
            }
            IconButton(onClick = { ChartsStore.load() }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新榜单",
                    tint = PomoTextDim,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Text(
            "榜单只有歌名 · 点 ⬇ 到音源搜索并提取音频（优先纯音乐 / 伴奏），存到手机本地",
            color = PomoTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "正在获取榜单…" else "暂无榜单内容\n点右上角刷新，或切换榜单来源",
                    color = PomoTextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(songs, key = { "${it.rank}-${it.title}" }) { song ->
                    ChartRow(
                        song = song,
                        task = tasks.values.firstOrNull { it.title == song.title && it.artist == song.artist },
                        onDownload = { SongDownloader.enqueue(song.title, song.artist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartRow(song: ChartSong, task: DlTask?, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .clickable { onDownload() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(PomoSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${song.rank}",
                color = if (song.rank <= 3) PomoPrimary else PomoTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = PomoText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = PomoTextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ChartAction(task, onDownload)
    }
}

@Composable
private fun ChartAction(task: DlTask?, onDownload: () -> Unit) {
    when (task?.stage) {
        null -> IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "下载", tint = PomoPrimary)
        }

        DlStage.QUEUED -> StageText("排队")
        DlStage.SEARCHING -> StageText("搜索中")
        DlStage.PICKING -> StageText("AI 选片")
        DlStage.EXTRACTING -> StageText("提取中")
        DlStage.DOWNLOADING -> MiniProgress(task.progress)
        DlStage.DONE -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "已完成",
            tint = PomoGreen,
            modifier = Modifier.size(22.dp),
        )

        DlStage.FAILED -> IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Refresh, contentDescription = "重试", tint = PomoPrimary)
        }
    }
}

@Composable
private fun StageText(text: String) {
    Text(
        text,
        color = PomoTextDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun MiniProgress(pct: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { pct.coerceIn(0, 100) / 100f },
            modifier = Modifier.size(26.dp),
            strokeWidth = 3.dp,
            color = PomoPrimary,
            trackColor = PomoSurfaceHigh,
        )
        Text(
            "$pct",
            color = PomoPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RowScope.SourcePill(
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
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
