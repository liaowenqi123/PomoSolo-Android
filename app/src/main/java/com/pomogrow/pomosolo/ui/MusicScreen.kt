package com.pomogrow.pomosolo.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.pomogrow.pomosolo.data.LocalImport
import com.pomogrow.pomosolo.data.MusicStore
import com.pomogrow.pomosolo.data.Song
import com.pomogrow.pomosolo.data.SongDownloader
import com.pomogrow.pomosolo.player.NowPlaying
import com.pomogrow.pomosolo.player.PlayerController
import com.pomogrow.pomosolo.player.Track
import com.pomogrow.pomosolo.ui.theme.PomoBg
import com.pomogrow.pomosolo.ui.theme.PomoGreen
import com.pomogrow.pomosolo.ui.theme.PomoPrimary
import com.pomogrow.pomosolo.ui.theme.PomoSurface
import com.pomogrow.pomosolo.ui.theme.PomoSurfaceHigh
import com.pomogrow.pomosolo.ui.theme.PomoText
import com.pomogrow.pomosolo.ui.theme.PomoTextDim

private const val TAB_ONLINE = 0
private const val TAB_CHARTS = 1
private const val TAB_LOCAL = 2

@Composable
fun MusicPage() {
    val snackbar = remember { SnackbarHostState() }
    val message by MusicStore.message.collectAsState()
    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbar.showSnackbar(m)
            MusicStore.consumeMessage()
        }
    }
    // 热榜下载器的提示（下载完成 / 失败原因）
    val dlMessage by SongDownloader.message.collectAsState()
    LaunchedEffect(dlMessage) {
        val m = dlMessage
        if (m != null) {
            snackbar.showSnackbar(m)
            SongDownloader.consumeMessage()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { MusicStore.importAudio(context.contentResolver, uri) }
        }
    }
    var tab by remember { mutableIntStateOf(TAB_ONLINE) }

    val catalog by MusicStore.catalog.collectAsState()
    val local by MusicStore.local.collectAsState()
    val imports by MusicStore.imports.collectAsState()
    val downloads by MusicStore.progress.collectAsState()
    val busy by MusicStore.busy.collectAsState()
    val refreshing by MusicStore.refreshing.collectAsState()

    // 在线目录（含内置曲）；本地列表 = 内置/已下载(在线) + 本地导入
    val localCatalog = remember(catalog, local) {
        catalog.filter { local.containsKey(it.file) }
    }
    val localList = remember(localCatalog, imports) {
        buildList {
            addAll(localCatalog.map {
                LocalRow(file = it.file, title = it.title, tag = it.tag)
            })
            for (i in imports) add(LocalRow(file = i.file, title = i.title, tag = null))
        }
    }

    Box(Modifier.fillMaxSize().background(PomoBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onlineCount = catalog.size,
                localCount = local.size + imports.size,
                downloading = busy.isNotEmpty(),
                pendingCount = catalog.count { !local.containsKey(it.file) },
                refreshing = refreshing,
                onRefresh = { MusicStore.refresh() },
                onDownloadAll = {
                    MusicStore.downloadAll(catalog.filter { !local.containsKey(it.file) })
                },
                onImport = { importer.launch(arrayOf("audio/*")) },
            )
            SegTabs(
                tab = tab,
                onlineCount = catalog.size,
                localCount = local.size + imports.size,
                onTab = { tab = it },
            )
            when (tab) {
                TAB_CHARTS -> ChartsTab()
                TAB_ONLINE -> OnlineList(
                    catalog = catalog,
                    local = local,
                    downloads = downloads,
                    busy = busy,
                    onPlayOrDownload = { song ->
                        val f = MusicStore.localUriString(song.file)
                        if (f != null) {
                            PlayerController.playQueue(queue(song, local), 0)
                        } else if (!busy.contains(song.file)) {
                            MusicStore.download(song)
                        }
                    },
                )
                else -> LocalList(
                    rows = localList,
                    localBytes = MusicStore.totalLocalBytes(),
                    onPlay = { index ->
                        val tracks = localTracks(localList)
                        PlayerController.playQueue(tracks, index)
                    },
                    onDelete = { row ->
                        catalog.firstOrNull { it.file == row.file }?.let { MusicStore.delete(it) }
                            ?: run {
                                val imp = LocalImport(row.file, row.title)
                                MusicStore.delete(
                                    Song(file = row.file, title = row.title, tag = null, bundled = false)
                                )
                            }
                    },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

private fun queue(song: Song, local: Map<String, String>): List<Track> {
    // 在线播放默认上下文：同页目录里“可离线/可下载”的歌
    val uri = MusicStore.localUriString(song.file) ?: MusicStore.remoteUrl(song.file)
    return listOf(Track(song.title, uri))
}

private fun localTracks(rows: List<LocalRow>): List<Track> =
    rows.map { Track(it.title, MusicStore.localUriString(it.file) ?: "") }.filter { it.uri.isNotEmpty() }

private data class LocalRow(val file: String, val title: String, val tag: String?)

// ---------------- Header ----------------

@Composable
private fun Header(
    onlineCount: Int,
    localCount: Int,
    downloading: Boolean,
    pendingCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDownloadAll: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("音乐", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PomoText)
            Spacer(Modifier.weight(1f))
            if (downloading) {
                Text("下载中…", color = PomoPrimary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }
            if (pendingCount > 0) {
                IconButton(onClick = onDownloadAll) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "下载全部（$pendingCount 首）",
                        tint = PomoPrimary,
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新曲库",
                    tint = PomoTextDim,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.FileDownload, contentDescription = "导入本地音乐", tint = PomoTextDim)
            }
        }
        Text(
            "原生下载器：曲库落盘到应用私有空间（可一键下载全部），飞行模式也能听",
            color = PomoTextDim,
            fontSize = 12.sp,
        )
    }
}

// ---------------- Tabs ----------------

@Composable
private fun SegTabs(tab: Int, onlineCount: Int, localCount: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .padding(4.dp),
    ) {
        SegTab("曲库 · $onlineCount", active = tab == TAB_ONLINE) { onTab(TAB_ONLINE) }
        SegTab("热榜", active = tab == TAB_CHARTS) { onTab(TAB_CHARTS) }
        SegTab("本地 · $localCount", active = tab == TAB_LOCAL) { onTab(TAB_LOCAL) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SegTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
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
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

// ---------------- Online list ----------------

@Composable
private fun OnlineList(
    catalog: List<Song>,
    local: Map<String, String>,
    downloads: Map<String, Int>,
    busy: Set<String>,
    onPlayOrDownload: (Song) -> Unit,
) {
    if (catalog.isEmpty()) {
        EmptyHint("曲库为空\n下拉… 点右上角刷新拉取服务器曲库")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(catalog) { _, song ->
            val downloaded = local.containsKey(song.file)
            val pct = downloads[song.file]
            val inFlight = busy.contains(song.file)
            SongCard(
                title = song.title,
                subtitle = if (song.bundled) "内置曲 · 可离线" else "服务器曲库",
                trailing = {
                    when {
                        downloaded -> Icon(
                            Icons.Filled.CheckCircle, contentDescription = "已下载",
                            tint = PomoGreen, modifier = Modifier.size(22.dp),
                        )
                        inFlight -> ProgressRing(pct)
                        else -> Icon(Icons.Filled.Download, contentDescription = "下载", tint = PomoPrimary)
                    }
                },
                onClick = { onPlayOrDownload(song) },
            )
        }
    }
}

@Composable
private fun ProgressRing(pct: Int?) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = {
                val p = pct ?: return@CircularProgressIndicator 0f
                p / 100f
            },
            modifier = Modifier.size(26.dp),
            strokeWidth = 3.dp,
            color = PomoPrimary,
            trackColor = PomoSurfaceHigh,
        )
        if (pct != null) {
            Text("$pct", color = PomoPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- Local list ----------------

@Composable
private fun LocalList(
    rows: List<LocalRow>,
    localBytes: Long,
    onPlay: (Int) -> Unit,
    onDelete: (LocalRow) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyHint("还没有本地歌曲\n点右上角“导入本地音乐”，或到「在线曲库」下载")
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "共 ${rows.size} 首 · ${formatBytes(localBytes)}（应用私有空间，随时离线播放）",
            color = PomoTextDim,
            fontSize = 12.sp,
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            SongCard(
                title = row.title,
                subtitle = if (row.tag != null) "内置曲 · ${row.tag}" else "本地",
                trailing = {
                    IconButton(onClick = { onDelete(row) }) {
                        Icon(
                            Icons.Filled.DeleteOutline, contentDescription = "删除",
                            tint = PomoTextDim,
                        )
                    }
                },
                onClick = { onPlay(index) },
            )
        }
    }
}

// ---------------- Shared row ----------------

@Composable
private fun SongCard(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PomoSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PomoSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = PomoPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PomoText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PomoTextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

// ---------------- helpers ----------------

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = PomoTextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format("%.1f MB", mb) else "${kb.toInt()} KB"
}
