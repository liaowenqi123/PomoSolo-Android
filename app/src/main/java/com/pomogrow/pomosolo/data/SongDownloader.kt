package com.pomogrow.pomosolo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale

/** 单曲下载阶段（对应桌面端 downloadQueue 的 status 语义）。 */
enum class DlStage(val label: String) {
    QUEUED("排队中"),
    SEARCHING("搜索音源"),
    PICKING("AI 选片"),
    EXTRACTING("提取音频"),
    DOWNLOADING("下载中"),
    DONE("已完成"),
    FAILED("失败"),
}

data class DlTask(
    val key: String,
    val title: String,
    val artist: String,
    val stage: DlStage,
    val progress: Int = 0,
    /** 选中的音源标题 / 失败原因 */
    val detail: String? = null,
)

/**
 * 单曲下载器：复刻桌面端「爬虫 + 音频提取」链路（`charts.rs` + `modules/downloader.rs`）。
 *
 * 流程：B站搜索 → 规则选片 → 取 DASH 音频流 → 下载到私有目录（m4a）→ 登记进本地库。
 *
 * 选片（对齐桌面端 `deepseek_select`）：默认调 DeepSeek 大模型，从搜索结果前 6 条里
 * 挑出最像纯音乐/原版的那条（提示词与 temperature=0 与桌面端完全一致）；
 * 未配置 API Key（且未关闭 AI 选片）时直接报错提示，关闭 AI 选片则退化为本地规则打分。
 *
 * 与桌面端的差异：桌面端拿到 m4a 后用 ffmpeg / symphonia+mp3lame 转 mp3；
 * 安卓端**跳过转码**直接落盘 m4a —— ExoPlayer / Media3 原生支持 AAC/M4A，无二次编码损失。
 *
 * 串行队列：一次只处理一首（对齐桌面端 DownloadDialog 的队列语义）。
 */
object SongDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = Channel<DlTask>(Channel.UNLIMITED)

    @Volatile
    private var pumpStarted = false

    private val _tasks = MutableStateFlow<Map<String, DlTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DlTask>> = _tasks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    private val activeStages = setOf(
        DlStage.QUEUED, DlStage.SEARCHING, DlStage.PICKING,
        DlStage.EXTRACTING, DlStage.DOWNLOADING,
    )

    /** 入队下载一首歌（已在处理中则忽略）。 */
    fun enqueue(title: String, artist: String) {
        val key = keyOf(title, artist)
        if (_tasks.value[key]?.stage in activeStages) return
        val task = DlTask(key = key, title = title, artist = artist, stage = DlStage.QUEUED)
        _tasks.value = _tasks.value + (key to task)
        queue.trySend(task)
        ensurePump()
    }

    /** 该曲目的当前任务（没有则返回 null）。 */
    fun taskOf(title: String, artist: String): DlTask? = _tasks.value[keyOf(title, artist)]

    private fun ensurePump() {
        if (pumpStarted) return
        pumpStarted = true
        scope.launch {
            for (task in queue) {
                runTask(task)
            }
        }
    }

    private suspend fun runTask(task: DlTask) {
        val key = task.key
        try {
            update(key) { it.copy(stage = DlStage.SEARCHING, progress = 0, detail = null) }
            // 对齐桌面端：用「歌名 - 歌手」搜索，取候选交给 AI 选片
            val videos = BiliClient.search("${task.title} - ${task.artist}")
            if (videos.isEmpty()) {
                fail(key, "没有搜索到相关音源")
                return
            }

            val aiConfig = AiPickStore.config.value
            val video: BiliVideo
            if (aiConfig.enabled) {
                if (!aiConfig.configured) {
                    fail(key, "请先在「设置 → AI 选片」配置 DeepSeek API Key")
                    return
                }
                update(key) { it.copy(stage = DlStage.PICKING) }
                val pickedIndex = try {
                    DeepSeekClient.select(videos, aiConfig.apiKey)
                } catch (e: Exception) {
                    fail(key, "AI 选片失败：${e.message ?: "调用异常"}")
                    return
                }
                val picked = pickedIndex?.let { videos.getOrNull(it) }
                if (picked == null) {
                    fail(key, "AI 判断没有合适的纯音乐版本")
                    return
                }
                video = picked
            } else {
                // 关闭 AI 选片时退化为本地规则打分
                val best = videos.maxByOrNull { score(it, task.title) }
                if (best == null || score(best, task.title) <= 0) {
                    fail(key, "没有找到合适的纯音乐/伴奏音源")
                    return
                }
                video = best
            }

            update(key) { it.copy(stage = DlStage.EXTRACTING, detail = video.title) }
            val url = BiliClient.audioUrl(video.bvid)
            if (url == null) {
                fail(key, "音频提取失败（可能被风控，稍后重试）")
                return
            }

            val dir = MusicStore.musicDirectory()
            val fileName = keyOf(task.title, task.artist)
            val dest = File(dir, fileName)
            if (dest.exists() && dest.length() > 0) {
                MusicStore.registerDownloadedFile(fileName, task.title)
                update(key) { it.copy(stage = DlStage.DONE, progress = 100, detail = "本地已存在") }
                return
            }

            val part = File(dir, "$fileName.part")
            update(key) { it.copy(stage = DlStage.DOWNLOADING, progress = 0) }
            BiliClient.download(url, part) { read, total ->
                if (total > 0) {
                    val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                    update(key) { it.copy(progress = pct) }
                }
            }
            if (!part.renameTo(dest)) throw IOException("保存失败")

            MusicStore.registerDownloadedFile(fileName, task.title)
            update(key) { it.copy(stage = DlStage.DONE, progress = 100, detail = video.title) }
            _message.value = "已下载：${task.title}"
        } catch (e: Exception) {
            fail(key, e.message ?: "下载失败")
        }
    }

    private fun fail(key: String, reason: String) {
        update(key) { it.copy(stage = DlStage.FAILED, detail = reason) }
        _message.value = reason
    }

    private inline fun update(key: String, transform: (DlTask) -> DlTask) {
        val cur = _tasks.value[key] ?: return
        _tasks.value = _tasks.value + (key to transform(cur))
    }

    // ---------------- 选片 ----------------

    private val badWords = listOf(
        "合集", "小时", "循环", "串烧", "10p", "100首", "教程", "教学", "扒谱",
        "翻唱", "鬼畜", "解说", "reaction", "直播", "电台", "整轨", "歌单",
    )

    private val goodWords = listOf(
        "纯音乐", "伴奏", "instrumental", "钢琴", "吉他", "轻音乐", "bgm", "八音盒",
        "纯享", "无损", "高音质", "治愈",
    )

    private fun score(video: BiliVideo, title: String): Int {
        val t = video.title.lowercase(Locale.ROOT)
        if (badWords.any { t.contains(it) }) return -100
        var s = 0
        goodWords.forEach { if (t.contains(it)) s += 4 }
        if (title.isNotBlank() && t.contains(title.lowercase(Locale.ROOT))) s += 3
        s += when {
            video.durationSec in 150..420 -> 6   // 2.5 ~ 7 分钟：正常单曲
            video.durationSec in 90..600 -> 3
            video.durationSec in 600..900 -> 0
            video.durationSec > 900 -> -6        // 超长合集
            else -> -1
        }
        return s
    }

    // ---------------- 工具 ----------------

    private fun keyOf(title: String, artist: String): String =
        cleanFileName("$title - $artist") + ".m4a"

    private fun cleanFileName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
}
