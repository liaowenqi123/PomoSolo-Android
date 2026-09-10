package com.pomogrow.pomosolo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** B站搜到的一条视频。 */
data class BiliVideo(
    val bvid: String,
    val title: String,
    val author: String,
    val durationSec: Int,
)

/**
 * B站公开接口的最小客户端（对齐桌面端 `src-tauri/src/modules/downloader.rs`）：
 * 搜索视频 → 取 cid → 取 DASH 音频流 → 下载音频流（m4a/AAC）。
 *
 * 桌面端用的也是这套公开接口（无签名、无 vkey），只需 UA + Referer。本实现额外：
 *  - 内存 CookieJar：先访问站点根拿 buvid3 等 Cookie，可显著降低 -412 风控概率；
 *  - 失败退避重试（0.8s → 2.4s → 7.2s + 抖动），对齐桌面端 3 次重试策略。
 *
 * 2026-09-10 实测（无 Cookie 直连）：搜索 200 / view 200 / playurl 200 且返回 7 条
 * dash.audio；连续强刷会返回 412，故必须带 Cookie 并退避。
 */
object BiliClient {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val REFERER = "https://www.bilibili.com/"

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                    for (c in cookies) {
                        list.removeAll { it.name == c.name }
                        list.add(c)
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    cookieStore[url.host]?.filter { it.matches(url) } ?: emptyList()
            })
            .build()
    }

    @Volatile
    private var warmed = false

    /** 预热：访问站点首页拿 buvid3 等 Cookie（只在首次真正需要时执行）。 */
    private fun warmUp() {
        if (warmed) return
        warmed = true
        try {
            client.newCall(
                Request.Builder().url("https://www.bilibili.com/").header("User-Agent", UA).build(),
            ).execute().use { it.body?.string() }
        } catch (_: Exception) {
            // 预热失败不影响后续（只是少了一层防风控）
        }
    }

    private fun getOnce(url: String, referer: String = REFERER): JSONObject? {
        warmUp()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful || body.isNullOrBlank()) null else JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 带退避重试的 JSON 请求；返回 null 表示彻底失败（含 -412 风控）。 */
    private suspend fun getJson(url: String, referer: String = REFERER): JSONObject? {
        var wait = 800L
        repeat(3) { attempt ->
            val json = getOnce(url, referer)
            if (json != null) {
                val code = json.optInt("code", 0)
                if (code == 0 || code == 200 || code == -400) return json
            }
            if (attempt < 2) {
                delay(wait + (0..300).random())
                wait *= 3
            }
        }
        return null
    }

    /** 关键词搜索视频（取前 20 条）。 */
    suspend fun search(keyword: String): List<BiliVideo> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" +
            URLEncoder.encode(keyword, "UTF-8") + "&page=1&pagesize=20"
        val json = getJson(url) ?: return@withContext emptyList()
        if (json.optInt("code", -1) != 0) return@withContext emptyList()
        val arr = json.optJSONObject("data")?.optJSONArray("result") ?: return@withContext emptyList()
        val out = ArrayList<BiliVideo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val bvid = o.optString("bvid")
            if (bvid.isEmpty()) continue
            out.add(
                BiliVideo(
                    bvid = bvid,
                    title = cleanTitle(o.optString("title")),
                    author = o.optString("author"),
                    durationSec = parseDuration(o.optString("duration")),
                ),
            )
        }
        out
    }

    /** bvid → DASH 音频流地址（取 bandwidth 最大的一条）。 */
    suspend fun audioUrl(bvid: String): String? = withContext(Dispatchers.IO) {
        val view = getJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid")
            ?: return@withContext null
        val cid = view.optJSONObject("data")?.optLong("cid", 0L) ?: 0L
        if (cid <= 0L) return@withContext null

        val play = getJson(
            "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&fnval=16&qn=64",
        ) ?: return@withContext null
        val dash = play.optJSONObject("data")?.optJSONObject("dash") ?: return@withContext null
        val audios = dash.optJSONArray("audio") ?: return@withContext null

        var best: String? = null
        var bestBandwidth = -1L
        for (i in 0 until audios.length()) {
            val a = audios.optJSONObject(i) ?: continue
            // 注意：B站把 & 写成 \u0026，JSONObject 会自动还原
            val u = a.optString("baseUrl").ifEmpty { a.optString("base_url") }
            val bw = a.optLong("bandwidth", 0L)
            if (u.isNotEmpty() && bw > bestBandwidth) {
                best = u
                bestBandwidth = bw
            }
        }
        best
    }

    /** 下载音频流到 [dest]（写入前由调用方准备 .part 文件）。 */
    suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .header("Origin", "https://www.bilibili.com")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                var read = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                    }
                }
                if (read <= 0L) throw IOException("音频流为空")
            }
        }

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun parseDuration(s: String): Int {
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
