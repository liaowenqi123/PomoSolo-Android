package com.pomogrow.pomosolo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 热榜来源（对齐桌面端 charts.rs 的 source: netease / qq）。 */
enum class ChartSource(val label: String, val shortLabel: String) {
    NETEASE("网易云音乐 · 热歌榜", "网易云热歌榜"),
    QQ("QQ音乐 · 热歌榜", "QQ音乐热歌榜"),
}

/** 榜单中的一首歌（只有文本，音频要另外去音源搜索提取）。 */
data class ChartSong(
    val rank: Int,
    val title: String,
    val artist: String,
    val album: String,
)

/**
 * 音乐热榜（对齐桌面端 `src-tauri/src/commands/charts.rs` 的 `charts_fetch`）。
 *
 * 桌面端直接爬音乐平台公开接口、不经过自建服务器；本实现同样直连：
 *  - 网易云：`music.163.com/api/playlist/detail?id=3778678`（3778678 = 热歌榜），
 *    失败时回退到 `music.163.com/discover/toplist?id=3778678` 的 HTML 内嵌 JSON；
 *  - QQ音乐：`c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=27`。
 * 均带 Referer + UA，无需签名、无需 Cookie（2026-09-10 实测网易云 200）。
 */
object ChartsStore {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val NETEASE_TOPID = "3778678" // 热歌榜
    private const val QQ_TOPID = "27"           // 热歌榜

    private const val LIMIT = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _songs = MutableStateFlow<List<ChartSong>>(emptyList())
    val songs: StateFlow<List<ChartSong>> = _songs.asStateFlow()

    private val _source = MutableStateFlow(ChartSource.NETEASE)
    val source: StateFlow<ChartSource> = _source.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun select(source: ChartSource) {
        if (_source.value == source && _songs.value.isNotEmpty()) return
        _source.value = source
        load()
    }

    fun load() {
        val current = _source.value
        scope.launch {
            _loading.value = true
            try {
                val list = when (current) {
                    ChartSource.NETEASE -> fetchNetease()
                    ChartSource.QQ -> fetchQq()
                }
                if (list.isEmpty()) {
                    _message.value = "未获取到榜单内容，请稍后重试或切换来源"
                } else {
                    // 只在仍处于同一来源时写入，避免快速切换导致的错位
                    if (_source.value == current) _songs.value = list
                }
            } catch (e: Exception) {
                _message.value = "榜单获取失败：${e.message ?: "网络错误"}"
            } finally {
                _loading.value = false
            }
        }
    }

    // ---------------- 网易云 ----------------

    private suspend fun fetchNetease(): List<ChartSong> {
        val api = "https://music.163.com/api/playlist/detail?id=$NETEASE_TOPID"
        getJson(api, "https://music.163.com/")?.let { json ->
            val tracks = json.optJSONObject("result")?.optJSONArray("tracks")
            parseNeteaseTracks(tracks)?.let { if (it.isNotEmpty()) return it }
        }
        // 备用：榜单页 HTML 内嵌 JSON（对齐桌面端 urldecode 方案）
        val html = getText("https://music.163.com/discover/toplist?id=$NETEASE_TOPID", "https://music.163.com/")
        if (!html.isNullOrEmpty()) {
            val marker = "id=\"song-list-pre-data\""
            val start = html.indexOf(marker)
            if (start >= 0) {
                val open = html.indexOf('>', start)
                val close = html.indexOf("</textarea>", open)
                if (open > 0 && close > open) {
                    val raw = html.substring(open + 1, close).trim()
                    val decoded = unescapeHtml(raw)
                    try {
                        parseNeteaseTracks(JSONArray(decoded))?.let { return it }
                    } catch (_: Exception) {
                        // 结构变化时忽略，交给上层提示失败
                    }
                }
            }
        }
        return emptyList()
    }

    private fun parseNeteaseTracks(tracks: JSONArray?): List<ChartSong>? {
        tracks ?: return null
        val out = ArrayList<ChartSong>()
        for (i in 0 until minOf(tracks.length(), LIMIT)) {
            val t = tracks.optJSONObject(i) ?: continue
            val title = t.optString("name").ifBlank { t.optString("title") }
            if (title.isBlank()) continue
            val artist = buildString {
                val artists = t.optJSONArray("artists") ?: t.optJSONArray("ar")
                if (artists != null) {
                    for (j in 0 until artists.length()) {
                        val a = artists.optJSONObject(j)?.optString("name").orEmpty()
                        if (a.isNotBlank()) {
                            if (isNotEmpty()) append(" / ")
                            append(a)
                        }
                    }
                }
            }
            val album = t.optJSONObject("album")?.optString("name")
                ?: t.optJSONObject("al")?.optString("name")
                ?: ""
            out.add(ChartSong(out.size + 1, title, artist.ifBlank { "未知歌手" }, album))
        }
        return out
    }

    // ---------------- QQ 音乐 ----------------

    private suspend fun fetchQq(): List<ChartSong> {
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg" +
            "?topid=$QQ_TOPID&needNewCode=1&uin=0&format=json&platform=h5&tpl=3&page=detail&type=top&song_begin=0&song_num=$LIMIT"
        val json = getJson(url, "https://y.qq.com/") ?: return emptyList()
        val list = json.optJSONArray("songlist") ?: return emptyList()
        val out = ArrayList<ChartSong>()
        for (i in 0 until minOf(list.length(), LIMIT)) {
            val item = list.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: item
            val title = data.optString("songname").ifBlank { data.optString("songorig") }
            if (title.isBlank()) continue
            val artist = buildString {
                val singers = data.optJSONArray("singer")
                if (singers != null) {
                    for (j in 0 until singers.length()) {
                        val n = singers.optJSONObject(j)?.optString("name").orEmpty()
                        if (n.isNotBlank()) {
                            if (isNotEmpty()) append(" / ")
                            append(n)
                        }
                    }
                }
            }
            out.add(
                ChartSong(
                    rank = out.size + 1,
                    title = title,
                    artist = artist.ifBlank { "未知歌手" },
                    album = data.optString("albumname"),
                ),
            )
        }
        return out
    }

    // ---------------- HTTP ----------------

    private suspend fun getText(url: String, referer: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getJson(url: String, referer: String): JSONObject? {
        val text = getText(url, referer) ?: return null
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    @Suppress("unused")
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
