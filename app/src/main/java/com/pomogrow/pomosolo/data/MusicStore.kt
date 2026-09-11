package com.pomogrow.pomosolo.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
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

/**
 * V1 music hub (native):
 *  - catalog manifest from server, cached locally for offline;
 *  - 3 bundled tracks seeded from assets/tracks (no network);
 *  - server download -> filesDir/music via .part + atomic rename, progress exposed;
 *  - SAF import of local audio;
 *  - state persisted in filesDir/music/index.json so downloads survive restarts.
 */
object MusicStore {

    private const val TAG = "MusicStore"

    // Music file CDN base (server hosts /music/*; verified reachable in v1 probe).
    private const val API_BASE = "https://api.pomogrow.top"

    // Catalog manifest (start.pomogrow.top site root; same origin as PWA).
    private const val MANIFEST_URL = "https://start.pomogrow.top/music-manifest.json"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private var musicDir: File = File("")

    // ---- UI state ----

    private val _catalog = MutableStateFlow<List<Song>>(emptyList())
    val catalog: StateFlow<List<Song>> = _catalog.asStateFlow()

    /** Present local files: song key -> stored file name. */
    private val _local = MutableStateFlow<Map<String, String>>(emptyMap())
    val local: StateFlow<Map<String, String>> = _local.asStateFlow()

    private val _imports = MutableStateFlow<List<LocalImport>>(emptyList())
    val imports: StateFlow<List<LocalImport>> = _imports.asStateFlow()

    /** Download progress: song key -> 0..100. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** One-shot UI message; UI calls [consumeMessage] after showing. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @Volatile
    private var initialized = false

    fun consumeMessage() {
        _message.value = null
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        musicDir = File(app.filesDir, "music").apply { mkdirs() }
        appScope.launch {
            try {
                seedBundled(app)
                loadIndex()
                refreshFromRemote()
            } catch (e: Exception) {
                Log.e(TAG, "init failed", e)
            }
        }
    }

    fun refresh() {
        appScope.launch { refreshFromRemote() }
    }

    fun download(song: Song) {
        if (_busy.value.contains(song.file) || _local.value.containsKey(song.file)) return
        appScope.launch { runDownload(song) }
    }

    /**
     * 批量下载（串行队列，对齐桌面端 DownloadDialog 的「队列」语义）：
     * 跳过已本地化 / 正在下载的曲目，逐个下载，单曲失败不影响后续。
     */
    fun downloadAll(songs: List<Song>) {
        appScope.launch {
            for (song in songs) {
                if (_local.value.containsKey(song.file)) continue
                if (_busy.value.contains(song.file)) continue
                runDownload(song)
            }
        }
    }

    /** 音乐落盘目录（热榜下载器写入同一个私有目录）。 */
    fun musicDirectory(): File = musicDir

    /**
     * 按标题查本地可播放 uri（自习室同步听歌用：房间内以「标题」作为 song_id）。
     * 先查内置/服务器曲库，再查本地导入与热榜下载。
     */
    fun localUriByTitle(title: String): String? {
        if (title.isBlank()) return null
        _catalog.value.firstOrNull { it.title == title }?.let { song ->
            localUriString(song.file)?.let { return it }
        }
        _imports.value.firstOrNull { it.title == title }?.let { imp ->
            localUriString(imp.file)?.let { return it }
        }
        return null
    }

    /**
     * 登记外部获取的音频（热榜下载器落盘的 m4a）进本地库并持久化，
     * 登记后它会出现在「本地音乐」里并可离线播放。
     */
    fun registerDownloadedFile(fileName: String, title: String) {
        _local.value = _local.value + (fileName to fileName)
        if (_imports.value.none { it.file == fileName }) {
            _imports.value = _imports.value + LocalImport(fileName, title)
        }
        persistIndex()
    }

    fun delete(song: Song) {
        appScope.launch {
            val name = _local.value[song.file] ?: return@launch
            _local.value = _local.value - song.file
            _imports.value = _imports.value.filterNot { it.file == song.file }
            File(musicDir, name).delete()
            persistIndex()
            _message.value = "已删除：${song.title}"
        }
    }

    /** Import audio via SAF. Returns null on success or an error message. */
    suspend fun importAudio(resolver: ContentResolver, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val display = queryDisplayName(resolver, uri) ?: "音乐"
            val ext = display.substringAfterLast('.', "mp3").ifBlank { "mp3" }
            var title = display.substringBeforeLast('.', display).trim().ifBlank { "音乐" }
            var target = "import_${System.currentTimeMillis()}_${title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.$ext"
            var n = 1
            while (File(musicDir, target).exists()) {
                target = "import_${System.currentTimeMillis()}_$n.$ext"
                n++
            }
            val ok = resolver.openInputStream(uri)?.use { input ->
                File(musicDir, target).outputStream().use { out -> input.copyTo(out) }
                true
            } ?: false
            if (!ok) return@withContext "无法读取所选文件"
            val imp = LocalImport(file = target, title = title)
            _imports.value = _imports.value + imp
            _local.value = _local.value + (imp.file to target)
            persistIndex()
            _message.value = "已导入：${imp.title}"
            null
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            "导入失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ================= internals =================

    private suspend fun refreshFromRemote() {
        if (_refreshing.value) return
        _refreshing.value = true
        try {
            val text = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(MANIFEST_URL).header("Accept", "application/json").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body?.string() ?: throw IOException("empty body")
                }
            }
            val remote = parseManifest(text)
            _catalog.value = mergeCatalog(remote)
            persistCatalog(remote)
            _message.value = null
        } catch (e: Exception) {
            Log.w(TAG, "refresh manifest failed: ${e.message}")
            if (_catalog.value.isEmpty()) {
                _catalog.value = bundledSongs()
            }
        } finally {
            _refreshing.value = false
        }
    }

    private fun mergeCatalog(remote: List<Song>): List<Song> {
        val bundled = bundledSongs()
        val remoteExtra = remote.filter { !it.bundled }
        val keys = HashSet<String>()
        val out = ArrayList<Song>()
        for (s in bundled) if (keys.add(s.file)) out.add(s)
        for (s in remoteExtra) if (keys.add(s.file)) out.add(s)
        return out
    }

    private fun bundledSongs(): List<Song> =
        listOf(
            "番茄倒数快一点 - 番茄钟.mp3",
            "番茄小宇宙 - 番茄钟.mp3",
            "Tick Tock, Take Control - 番茄钟.mp3",
        ).map { name ->
            Song(file = name, title = name.substringBeforeLast('.', name), tag = null, bundled = true)
        }

    /** First launch: copy bundled mp3 from assets/tracks into private dir. */
    private fun seedBundled(context: Context) {
        val bundled = bundledSongs()
        val already = musicDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        for (song in bundled) {
            if (song.file in already) continue
            try {
                context.assets.open("tracks/${song.file}").use { input ->
                    File(musicDir, song.file).outputStream().use { out -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "seed bundled failed: ${song.file}", e)
            }
        }
        _local.value = (musicDir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name != "index.json" && !it.name.endsWith(".part") }
            .associate { it.name to it.name }
    }

    private suspend fun runDownload(song: Song) {
        _busy.value = _busy.value + song.file
        try {
            val url = remoteUrl(song.file)
            val req = Request.Builder().url(url).build()
            val tmp = File(musicDir, "${song.file}.part")
            withContext(Dispatchers.IO) {
                var total = -1L
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    total = resp.body?.contentLength() ?: -1L
                    var written = 0L
                    tmp.outputStream().use { out ->
                        resp.body!!.byteStream().use { input ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buf)
                                if (read < 0) break
                                out.write(buf, 0, read)
                                written += read
                                if (total > 0) {
                                    val pct = (written * 100 / total).toInt().coerceIn(0, 100)
                                    _progress.value = _progress.value + (song.file to pct)
                                }
                            }
                        }
                    }
                }
            }
            val dest = File(musicDir, song.file)
            if (!tmp.renameTo(dest)) throw IOException("rename failed")
            _local.value = _local.value + (song.file to song.file)
            persistIndex()
            _message.value = "下载完成：${song.title}"
        } catch (e: Exception) {
            Log.e(TAG, "download ${song.file} failed", e)
            _message.value = "下载失败：${song.title}（${e.message ?: "网络错误"}）"
            File(musicDir, "${song.file}.part").delete()
        } finally {
            _busy.value = _busy.value - song.file
            _progress.value = _progress.value - song.file
        }
    }

    // ---- manifest parse ----

    private fun parseManifest(text: String): List<Song> {
        val root = JSONObject(text)
        val arr: JSONArray = root.optJSONArray("songs") ?: JSONArray()
        val out = ArrayList<Song>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val source = o.optString("source", "library")
            out.add(
                Song(
                    file = name,
                    title = name.substringBeforeLast('.', name),
                    tag = if (o.isNull("tag")) null else o.optString("tag").ifBlank { null },
                    bundled = source == "bundled",
                )
            )
        }
        return out
    }

    // ---- index persistence ----

    private fun persistCatalog(remote: List<Song>) {
        val obj = JSONObject()
        obj.put("catalog", JSONArray().apply {
            for (s in remote) {
                put(
                    JSONObject().apply {
                        put("name", s.file)
                        put("source", if (s.bundled) "bundled" else "library")
                        if (s.tag != null) put("tag", s.tag)
                    }
                )
            }
        })
        writeIndex(obj)
    }

    private fun persistIndex() {
        val obj = JSONObject()
        obj.put("imported", JSONArray().apply {
            for (i in _imports.value) {
                put(JSONObject().apply { put("file", i.file); put("title", i.title) })
            }
        })
        writeIndex(obj)
    }

    @Synchronized
    private fun writeIndex(obj: JSONObject) {
        try {
            val f = File(musicDir, "index.json")
            val tmp = File(musicDir, "index.json.tmp")
            tmp.writeText(obj.toString())
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        } catch (e: Exception) {
            Log.e(TAG, "persist index failed", e)
        }
    }

    private fun loadIndex() {
        try {
            val f = File(musicDir, "index.json")
            if (!f.exists()) {
                persistIndex()
                return
            }
            val root = JSONObject(f.readText())
            val imports = mutableListOf<LocalImport>()
            val jImp = root.optJSONArray("imported") ?: JSONArray()
            for (i in 0 until jImp.length()) {
                val o = jImp.optJSONObject(i) ?: continue
                val file = o.optString("file")
                if (file.isEmpty()) continue
                if (File(musicDir, file).exists()) {
                    imports.add(LocalImport(file, o.optString("title").ifBlank { file }))
                }
            }
            _imports.value = imports
            val files = musicDir.listFiles()
                ?.filter { it.isFile && it.name != "index.json" && it.name != "index.json.tmp" && !it.name.endsWith(".part") }
                ?.associate { it.name to it.name } ?: emptyMap()
            _local.value = files

            val cachedRemote = root.optJSONArray("catalog") ?: JSONArray()
            if (cachedRemote.length() > 0) {
                val list = parseManifest(cachedRemote.toString())
                _catalog.value = mergeCatalog(list)
            } else {
                _catalog.value = bundledSongs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "load index failed", e)
            _catalog.value = bundledSongs()
        }
    }

    // ---- helpers ----

    fun remoteUrl(file: String): String = "$API_BASE/music/${file.urlEncoded()}"

    /** Playable local file uri (file://...); null when not localised yet. */
    fun localUriString(songFile: String): String? {
        val name = _local.value[songFile] ?: return null
        return Uri.fromFile(File(musicDir, name)).toString()
    }

    fun localFilePath(songFile: String): String? = _local.value[songFile]

    fun localFileSize(songFile: String): Long {
        val name = _local.value[songFile] ?: return 0L
        return File(musicDir, name).length()
    }

    fun totalLocalBytes(): Long {
        var sum = 0L
        for ((_, name) in _local.value) {
            sum += File(musicDir, name).length()
        }
        return sum
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }
}

/** Percent-encode a file name for a URL path segment (RFC 3986). */
fun String.urlEncoded(): String {
    val hex = "0123456789ABCDEF"
    val sb = StringBuilder()
    val bytes = toByteArray(Charsets.UTF_8)
    fun isUnreserved(b: Int): Boolean {
        return (b >= 'a'.code && b <= 'z'.code) || (b >= 'A'.code && b <= 'Z'.code) ||
            (b >= '0'.code && b <= '9'.code) || b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code
    }
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        if (isUnreserved(v)) {
            sb.append(v.toChar())
        } else {
            sb.append('%')
            sb.append(hex[v ushr 4])
            sb.append(hex[v and 0x0F])
        }
    }
    return sb.toString()
}
