package com.pomogrow.pomosolo.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** P2P 传输进度（发送/接收共用，视角色而定）。 */
data class P2PProgress(
    val active: Boolean = false,
    val peerId: String = "",
    val transferredBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0,
    val connected: Boolean = false,
    val role: String = "",
)

/**
 * P2P 数据传输（WebRTC DataChannel），协议对齐桌面端/PWA 的 `src/p2p.ts`：
 *
 *  - 信令走现有 WS：`peer:offer` / `peer:answer` / `peer:ice`（服务端只做定向转发，带 `tag` 区分并发连接）；
 *  - ICE：4 个 STUN 并行（cloudflare / miwifi / bilibili / google），**无 TURN**
 *    —— 对称 NAT 下可能打不通，由上层回退服务器中转（`music:request_song` 等）；
 *  - DataChannel：label `"p2p"`、ordered/reliable；
 *  - 控制消息（String JSON）：`{"t":"meta","size":N,"totalChunks":M,"chunkSize":K}`；
 *  - 数据帧（Binary）：**4 字节大端 chunk index + payload**，128KB/片；
 *  - 背压：`bufferedAmount` 超过阈值即等待，避免发送端缓冲溢出静默死亡。
 *
 * 为什么要引入 native 库：PWA/桌面端跑在浏览器/WebView 里，WebRTC 是内核自带的；
 * 原生 Android 没有浏览器内核，必须自带 libwebrtc（`io.github.webrtc-sdk:android`）。
 */
object P2PTransfer {

    private const val CHUNK_SIZE = 128 * 1024
    private const val BACKPRESSURE_BYTES = 4L * 1024 * 1024

    private val STUN_URLS = listOf(
        "stun:stun.cloudflare.com:3478",
        "stun:stun.miwifi.com:3478",
        "stun:stun.chat.bilibili.com:3478",
        "stun:stun.l.google.com:19302",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var factory: PeerConnectionFactory? = null

    private val _progress = MutableStateFlow(P2PProgress())
    val progress: StateFlow<P2PProgress> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /** 由 StudyRoomStore 注入：把信令发到房间（服务端定向转发给 to_user_id）。 */
    var sendSignal: ((type: String, toUserId: String, payload: JSONObject) -> Unit)? = null

    private var pc: PeerConnection? = null
    private var dc: DataChannel? = null
    private var peerId = ""
    private var isOfferer = false
    private var totalBytes = 0L
    private var transferred = 0L
    private var startedAt = 0L
    private var readChunk: ((Int) -> ByteArray)? = null
    private var onChunk: ((Int, ByteArray) -> Unit)? = null
    private var pendingCandidates = mutableListOf<IceCandidate>()

    fun init(context: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    // ---------------- 对外入口 ----------------

    /**
     * 发起一次 P2P 传输（offerer = 发送方）。
     * [readChunk] 按分片序号返回原始字节；[totalBytes] 决定分片总数。
     */
    fun startSend(toUserId: String, readChunk: (Int) -> ByteArray, totalBytes: Long) {
        val f = factory ?: run {
            _message.value = "WebRTC 未初始化"
            return
        }
        close()
        peerId = toUserId
        isOfferer = true
        this.readChunk = readChunk
        this.totalBytes = totalBytes
        transferred = 0L
        startedAt = SystemClock.elapsedRealtime()
        _progress.value = P2PProgress(active = true, peerId = toUserId, totalBytes = totalBytes, role = "发送")

        pc = f.createPeerConnection(rtcConfig(), observer)
        val init = DataChannel.Init().apply { ordered = true }
        dc = pc?.createDataChannel("p2p", init)?.also { wireChannel(it) }

        pc?.createOffer(sdpObserver, MediaConstraints())
    }

    /** 收到对端 offer（answerer = 接收方）。 */
    private fun acceptOffer(fromUserId: String, sdp: String) {
        val f = factory ?: return
        close()
        peerId = fromUserId
        isOfferer = false
        transferred = 0L
        startedAt = SystemClock.elapsedRealtime()
        _progress.value = P2PProgress(active = true, peerId = fromUserId, role = "接收")

        pc = f.createPeerConnection(rtcConfig(), observer)
        pc?.setRemoteDescription(
            sdpObserver,
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    /** 服务端转发来的 peer:* 信令。 */
    fun onSignal(msg: JSONObject) {
        val from = msg.optString("from_user_id")
        when (msg.optString("type")) {
            "peer:offer" -> {
                val sdp = msg.optJSONObject("sdp")?.optString("sdp").orEmpty()
                if (sdp.isNotBlank()) acceptOffer(from, sdp)
            }

            "peer:answer" -> {
                val sdp = msg.optJSONObject("sdp")?.optString("sdp").orEmpty()
                if (sdp.isNotBlank()) {
                    pc?.setRemoteDescription(
                        sdpObserver,
                        SessionDescription(SessionDescription.Type.ANSWER, sdp),
                    )
                }
            }

            "peer:ice" -> {
                val c = msg.optJSONObject("candidate") ?: return
                val candidate = IceCandidate(
                    c.optString("sdpMid"),
                    c.optInt("sdpMLineIndex", 0),
                    c.optString("candidate"),
                )
                val connection = pc ?: return
                if (connection.remoteDescription == null) {
                    pendingCandidates.add(candidate)
                } else {
                    connection.addIceCandidate(candidate)
                }
            }
        }
    }

    /** 便捷：随机数据连通性/速度测试（对齐桌面端 P2P 测试工具）。 */
    fun startSpeedTest(toUserId: String, sizeBytes: Long = 2L * 1024 * 1024) {
        val total = sizeBytes.toInt()
        startSend(toUserId, readChunk = { index ->
            val start = index * CHUNK_SIZE
            val len = minOf(CHUNK_SIZE, total - start)
            Random.nextBytes(ByteArray(len))
        }, totalBytes = sizeBytes.toLong())
    }

    fun close() {
        runCatching { dc?.close() }
        runCatching { pc?.close() }
        dc = null
        pc = null
        readChunk = null
        onChunk = null
        pendingCandidates.clear()
        _progress.value = _progress.value.copy(active = false, connected = false)
    }

    // ---------------- 内部 ----------------

    private fun rtcConfig(): PeerConnection.RTCConfiguration {
        val servers = STUN_URLS.map { PeerConnection.IceServer.builder(it).createIceServer() }
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
    }

    private fun wireChannel(channel: DataChannel) {
        dc = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                val state = channel.state()
                if (state == DataChannel.State.OPEN) {
                    _progress.value = _progress.value.copy(connected = true)
                    if (isOfferer) pumpSend()
                } else if (state == DataChannel.State.CLOSED) {
                    _progress.value = _progress.value.copy(active = false, connected = false)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                if (buffer.binary) {
                    if (bytes.size < 4) return
                    val index = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                    val payload = bytes.copyOfRange(4, bytes.size)
                    transferred += payload.size
                    onChunk?.invoke(index, payload)
                    updateProgress()
                } else {
                    // 控制消息（meta 等）
                    val text = String(bytes, Charsets.UTF_8)
                    runCatching {
                        val json = JSONObject(text)
                        if (json.optString("t") == "meta") {
                            totalBytes = json.optLong("size")
                            _progress.value = _progress.value.copy(totalBytes = totalBytes)
                        }
                    }
                }
            }
        })
    }

    /** 发送端：meta + 逐片发送（带背压）。 */
    private fun pumpSend() {
        val reader = readChunk ?: return
        val total = totalBytes
        scope.launch {
            val totalChunks = ((total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            val meta = JSONObject()
                .put("t", "meta")
                .put("size", total)
                .put("totalChunks", totalChunks)
                .put("chunkSize", CHUNK_SIZE)
            sendText(meta.toString())

            for (i in 0 until totalChunks) {
                val data = try {
                    reader(i)
                } catch (e: Exception) {
                    finish("读取分片失败：${e.message}")
                    return@launch
                }
                waitForBuffer()
                val frame = ByteBuffer.allocate(4 + data.size).order(ByteOrder.BIG_ENDIAN)
                frame.putInt(i)
                frame.put(data)
                frame.flip()
                val ok = dc?.send(DataChannel.Buffer(frame, true)) ?: false
                if (!ok) {
                    finish("发送失败（连接已断开）")
                    return@launch
                }
                transferred += data.size
                updateProgress()
            }
            finish(
                "P2P 发送完成：${transferred / 1024} KB · " +
                    "${speedMbps()} Mbps · ${peerId.take(8)}",
            )
        }
    }

    private suspend fun waitForBuffer() {
        while ((dc?.bufferedAmount() ?: 0L) > BACKPRESSURE_BYTES) {
            delay(20)
        }
    }

    private fun sendText(text: String) {
        val buf = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
        dc?.send(DataChannel.Buffer(buf, false))
    }

    private fun updateProgress() {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val speed = if (elapsed > 0) transferred * 1000 / elapsed else 0
        _progress.value = _progress.value.copy(
            transferredBytes = transferred,
            speedBps = speed,
        )
    }

    private fun speedMbps(): String {
        val bps = if (speedBpsValue() > 0) speedBpsValue() else 0
        return "%.1f".format(bps / 1024.0 / 1024.0 * 8)
    }

    private fun speedBpsValue(): Long = _progress.value.speedBps

    private fun finish(note: String) {
        updateProgress()
        _progress.value = _progress.value.copy(active = false, connected = false)
        _message.value = note
        close()
    }

    private fun fail(reason: String) {
        _progress.value = _progress.value.copy(active = false, connected = false)
        _message.value = reason
        close()
    }

    // ---------------- WebRTC 回调 ----------------

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {
            desc ?: return
            pc?.setLocalDescription(this, desc)
        }

        override fun onSetSuccess() {
            if (!isOfferer) {
                // answerer：remote=offer 已设置 → 生成 answer
                if (pc?.localDescription == null) {
                    pc?.createAnswer(this, MediaConstraints())
                }
            } else {
                // offerer：local=offer 已设置 → 发送 offer 信令
                val sdp = pc?.localDescription?.description ?: return
                sendSignal?.invoke(
                    "peer:offer",
                    peerId,
                    JSONObject().put("sdp", JSONObject().put("type", "offer").put("sdp", sdp)),
                )
            }
        }

        override fun onCreateFailure(error: String?) = fail("创建 SDP 失败：$error")

        override fun onSetFailure(error: String?) = fail("设置 SDP 失败：$error")
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            sendSignal?.invoke(
                "peer:ice",
                peerId,
                JSONObject().put(
                    "candidate",
                    JSONObject()
                        .put("candidate", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex),
                ),
            )
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> _progress.value = _progress.value.copy(connected = true)

                PeerConnection.IceConnectionState.FAILED -> fail("P2P 打洞失败（对称 NAT？）可回退服务器中转")
                else -> Unit
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                _progress.value = _progress.value.copy(connected = true)
            }
        }

        override fun onDataChannel(channel: DataChannel?) {
            // answerer 侧拿到对端创建的通道（也是接收数据的地方）
            channel ?: return
            wireChannel(channel)
        }
    }
}
