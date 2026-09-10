package com.pomogrow.pomosolo.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 可播放的一首（标题 + 数据源 uri：本地文件 file:// 或在线 https）。 */
data class Track(
    val title: String,
    val uri: String,
)

/** 当前正在播放的信息（驱动底部迷你播放条 / 展开面板）。 */
data class NowPlaying(
    val title: String,
    val isLocal: Boolean,
)

/**
 * V1 播放引擎：Media3 ExoPlayer 封装，暴露播放状态流。
 *
 * 关键点（曾导致“没声音”的原因）：
 *  - 必须显式设置 AudioAttributes(USAGE_MEDIA / CONTENT_TYPE_MUSIC) 并请求音频焦点，
 *    否则部分设备/ROM 会按“通知音/系统音”通道处理或直接不申请焦点而不出声；
 *  - volume 显式置 1f，避免被系统音频流策略压到 0；
 *  - 播放错误必须暴露给 UI（原先静默吞掉，用户只看到“点了没反应/没声音”）。
 */
object PlayerController {

    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trackQueue: List<Track> = emptyList()

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** 播放失败原因（一次性，UI 消费后清空）。 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var initialized = false

    fun consumeError() {
        _error.value = null
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(audioAttrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                volume = 1f
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playing.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _durationMs.value = duration.coerceAtLeast(0)
                                _playing.value = isPlaying
                            }
                            Player.STATE_ENDED -> _playing.value = false
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _playing.value = false
                        _error.value = friendlyError(error)
                    }
                })
            }
        // 进度节流更新（500ms）
        scope.launch {
            while (true) {
                if (_now.value != null) {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0)
                    val d = player.duration
                    if (d > 0) _durationMs.value = d
                }
                delay(500)
            }
        }
    }

    /** 播放一个队列，startIndex 定位起点。 */
    fun playQueue(queue: List<Track>, startIndex: Int) {
        val playable = queue.filter { it.uri.isNotBlank() }
        if (playable.isEmpty()) return
        val idx = startIndex.coerceIn(0, playable.size - 1)
        trackQueue = playable
        _error.value = null
        val mediaItems = playable.map { t ->
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).build())
                .build()
        }
        player.setMediaItems(mediaItems, idx, 0L)
        player.prepare()
        player.play()
        updateNow(idx)
    }

    /** 播放单曲（无上下文队列）。 */
    fun playTrack(track: Track) = playQueue(listOf(track), 0)

    fun toggle() {
        if (_now.value == null) return
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            else -> player.play()
        }
    }

    fun seekTo(ms: Long) {
        if (_now.value != null) player.seekTo(ms.coerceAtLeast(0))
    }

    fun next() {
        val cur = player.currentMediaItemIndex
        if (cur < player.mediaItemCount - 1) {
            player.seekToNextMediaItem()
            player.play()
            updateNow(cur + 1)
        }
    }

    fun prev() {
        val cur = player.currentMediaItemIndex
        if (cur > 0) {
            player.seekToPreviousMediaItem()
            player.play()
            updateNow(cur - 1)
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _now.value = null
        _playing.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        trackQueue = emptyList()
    }

    fun release() {
        if (initialized) player.release()
    }

    private fun updateNow(index: Int) {
        val t = trackQueue.getOrNull(index) ?: return
        _now.value = NowPlaying(title = t.title, isLocal = !t.uri.startsWith("http"))
        _playing.value = player.isPlaying
    }

    private fun friendlyError(e: PlaybackException): String {
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "文件不存在或已被删除"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器返回错误（文件可能未托管）"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接失败，可先下载到本地再听"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "音频格式不支持"
            else -> "播放失败：${e.cause?.message ?: e.errorCodeName}"
        }
    }
}
