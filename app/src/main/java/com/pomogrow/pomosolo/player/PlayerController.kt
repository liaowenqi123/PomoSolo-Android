package com.pomogrow.pomosolo.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

/** V1 播放引擎：Media3 ExoPlayer 封装，暴露播放状态流。 */
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

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playing.value = isPlaying
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playing.value = false
                    }
                }
            })
        }
        // 进度节流更新（500ms）
        scope.launch {
            while (true) {
                if (_now.value != null) {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0)
                    _durationMs.value = player.duration.coerceAtLeast(0)
                }
                delay(500)
            }
        }
    }

    /** 播放一个队列，startIndex 定位起点。 */
    fun playQueue(queue: List<Track>, startIndex: Int) {
        if (queue.isEmpty()) return
        val idx = startIndex.coerceIn(0, queue.size - 1)
        trackQueue = queue
        val mediaItems = queue.map { t ->
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
        if (player.isPlaying) player.pause() else player.play()
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
}
