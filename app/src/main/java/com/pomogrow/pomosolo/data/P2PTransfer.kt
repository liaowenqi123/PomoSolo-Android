package com.pomogrow.pomosolo.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/** 一次 P2P 传输的结果（对齐桌面端 `p2p:test_result` 的字段）。 */
data class P2PResult(
    val ok: Boolean,
    val ms: Long,
    val speedBps: Long,
    val bytes: Long,
    val error: String? = null,
)

/** UI 展示用的实时状态。 */
data class P2PProgress(
    val active: Boolean = false,
    val peerId: String = "",
    val role: String = "",
    val transferredBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0,
    val connected: Boolean = false,
)

/**
 * P2P 数据传输（WebRTC DataChannel），协议对齐桌面端/PWA 的 `src/p2p.ts`：
 *
 *  - 信令走现有 WS：`peer:offer/answer/ice`（服务端按 to_user_id 定向转发，附加 from_user_id，
 *    `tag` 原样透传）——同一条连接用 `peerId:tag` 作为路由键，支持同一对端多条并发连接；
 *  - DataChannel：label `"p2p"`、ordered/reliable；
 *  - 控制消息（String JSON）：`{"t":"meta","size":N,"totalChunks":M,"chunkSize":K}`、
 *    `{"t":"duplex_switch"}`、`{"t":"duplex_done"}`；
 *  - 数据帧（Binary）：**4 字节大端 chunk index + payload**；
 *  - 背压：`bufferedAmount` 超阈值时等待，避免缓冲溢出导致发送端静默死亡。
 *
 * 支持两种模式：
 *  - 单向（默认）：offerer 推送、answerer 接收；
 *  - **duplex-test**（桌面端测试工具用的模式）：同一连接先由 offerer 推一程，
 *    完成后发 `duplex_switch`，answerer 再推一程，完成后发 `duplex_done` —— 一条连接测双方向。
 *
 * 为什么需要 native 库：PWA/桌面端跑在浏览器/WebView 内，WebRTC 是内核自带的；
 * 原生 Android 无浏览器内核，只能自带 libwebrtc。
 */
object P2PTransfer {

    private const val CHUNK_SIZE = 64 * 1024
    private const val BACKPRESSURE_BYTES = 4L * 1024 * 1024
    private const val OFFER_WAIT_MS = 12_000L

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

    // ---------------- 会话 ----------------

    private class Session(val key: String, val peerId: String, val tag: String) {
        var pc: PeerConnection? = null
        var dc: DataChannel? = null
        var isOfferer = false
        var duplex = false
        var totalBytes = 0L
        var chunkSize = CHUNK_SIZE
        var pushed = 0L
        var received = 0L
        var pushStartedAt = 0L
        var recvStartedAt = 0L
        var pushDone = false
        var peerPushDone = false
        var localDescriptionSet = false
        var answerSent = false
        var readChunk: ((Int) -> ByteArray)? = null
        var onResult: ((P2PResult) -> Unit)? = null
        var onProgress: ((Long, Long) -> Unit)? = null
        var offerTimeout: Job? = null
        val pendingCandidates = mutableListOf<IceCandidate>()
        var finished = false
    }

    private val sessions = mutableMapOf<String, Session>()

    /** 等待对端 offer 的接收端（answerer 挂起，12s 超时）。 */
    private val pendingReceivers = mutableMapOf<String, Session>()

    private fun key(peerId: String, tag: String) = "$peerId:$tag"

    fun init(context: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    /**
     * 发起一次双向测速（offerer）。
     * 对端需已通过 `p2p:test_request` 挂起接收（桌面端会自动挂起）。
     */
    fun startDuplexTest(
        peerId: String,
        tag: String,
        bytes: Long = 2L * 1024 * 1024,
        onResult: (P2PResult) -> Unit,
    ) {
        val total = bytes.toInt()
        send(
            peerId = peerId,
            tag = tag,
            totalBytes = bytes,
            readChunk = { index ->
                val start = index * CHUNK_SIZE
                val len = minOf(CHUNK_SIZE, total - start)
                Random.nextBytes(ByteArray(len))
            },
            onResult = onResult,
        )
    }

    /** 发起传输（offerer；duplex=true 时使用双向测速时序）。 */
    fun send(
        peerId: String,
        tag: String,
        totalBytes: Long,
        readChunk: (Int) -> ByteArray,
        duplex: Boolean = true,
        onResult: (P2PResult) -> Unit,
    ) {
        val f = factory ?: run {
            onResult(P2PResult(false, 0, 0, 0, "WebRTC 未初始化"))
            return
        }
        val k = key(peerId, tag)
        closeSession(k)
        val session = Session(k, peerId, tag).apply {
            isOfferer = true
            this.duplex = duplex
            this.totalBytes = totalBytes
            this.chunkSize = CHUNK_SIZE
            this.readChunk = readChunk
            this.onResult = onResult
            pushStartedAt = SystemClock.elapsedRealtime()
        }
        sessions[k] = session
        _progress.value = P2PProgress(
            active = true, peerId = peerId, role = "发送",
            totalBytes = totalBytes,
        )

        session.pc = f.createPeerConnection(rtcConfig(), observer(session))
        val init = DataChannel.Init().apply { ordered = true }
        session.dc = session.pc?.createDataChannel("p2p", init)?.also { wireChannel(session, it) }
        session.pc?.createOffer(sdpObserver(session), MediaConstraints())
    }

    /**
     * 挂起等待对端 offer（answerer）。对齐桌面端 `p2pReceive`：桌面端收到
     * `p2p:test_request` 后会自动挂起，因此安卓发起时对端已就绪；反之亦然。
     */
    fun receive(
        peerId: String,
        tag: String,
        timeoutMs: Long = OFFER_WAIT_MS,
        onResult: (P2PResult) -> Unit,
    ) {
        val k = key(peerId, tag)
        closeSession(k)
        val session = Session(k, peerId, tag).apply {
            isOfferer = false
            duplex = true
            this.onResult = onResult
        }
        pendingReceivers[k] = session
        session.offerTimeout = scope.launch {
            delay(timeoutMs)
            if (pendingReceivers.remove(k) != null && !session.finished) {
                session.finished = true
                onResult(P2PResult(false, 0, 0, 0, "等待对端 offer 超时（${timeoutMs / 1000}s）"))
            }
        }
    }

    /** 服务端转发来的 peer:* 信令（按 peerId:tag 路由）。 */
    fun onSignal(msg: JSONObject) {
        val f = factory ?: return
        val from = msg.optString("from_user_id")
        val tag = msg.optString("tag")
        val k = key(from, tag)
        val type = msg.optString("type")

        if (type == "peer:offer") {
            val sdp = msg.optJSONObject("sdp")?.optString("sdp").orEmpty()
            if (sdp.isBlank()) return
            // 优先复用已挂起的接收会话；没有挂起则直接接受（兼容未预告的 offer）
            val session = pendingReceivers.remove(k) ?: Session(k, from, tag).also {
                it.isOfferer = false
                it.duplex = true
                sessions[k] = it
            }
            session.offerTimeout?.cancel()
            sessions[k] = session
            session.pc = f.createPeerConnection(rtcConfig(), observer(session))
            session.pc?.setRemoteDescription(
                sdpObserver(session),
                SessionDescription(SessionDescription.Type.OFFER, sdp),
            )
            _progress.value = P2PProgress(active = true, peerId = from, role = "接收")
            return
        }

        val session = sessions[k] ?: return
        when (type) {
            "peer:answer" -> {
                val sdp = msg.optJSONObject("sdp")?.optString("sdp").orEmpty()
                if (sdp.isBlank()) return
                session.pc?.setRemoteDescription(
                    sdpObserver(session),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp),
                )
            }

            "peer:ice" -> {
                val c = msg.optJSONObject("candidate") ?: return
                val candidate = IceCandidate(
                    c.optString("sdpMid"),
                    c.optInt("sdpMLineIndex", 0),
                    c.optString("candidate"),
                )
                val connection = session.pc ?: return
                if (connection.remoteDescription == null) {
                    session.pendingCandidates.add(candidate)
                } else {
                    connection.addIceCandidate(candidate)
                }
            }
        }
    }

    /** 兼容自习室里的"点成员发起测速"（单连接、非 duplex）。 */
    fun startSpeedTest(toUserId: String, sizeBytes: Long = 2L * 1024 * 1024) {
        startDuplexTest(toUserId, tag = "app-test", bytes = sizeBytes) { result ->
            _message.value = if (result.ok) {
                "P2P 测速完成：%.1f Mbps（%.1f MB / %d ms）".format(
                    result.speedBps / 1024.0 / 1024.0 * 8,
                    result.bytes / 1024.0 / 1024.0,
                    result.ms,
                )
            } else {
                "P2P 测速失败：${result.error ?: "未知原因"}"
            }
        }
    }

    fun close() {
        sessions.keys.toList().forEach { closeSession(it) }
        pendingReceivers.clear()
        _progress.value = P2PProgress()
    }

    private fun closeSession(k: String) {
        sessions.remove(k)?.let { s ->
            s.offerTimeout?.cancel()
            runCatching { s.dc?.close() }
            runCatching { s.pc?.close() }
        }
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

    private fun wireChannel(session: Session, channel: DataChannel) {
        session.dc = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) {
                    session.recvStartedAt = SystemClock.elapsedRealtime()
                    _progress.value = _progress.value.copy(connected = true)
                    if (session.isOfferer) pushChunkBurst(session)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                if (buffer.binary) {
                    if (bytes.size < 4) return
                    session.received += (bytes.size - 4)
                    onProgressTick(session)
                } else {
                    handleControl(session, String(bytes, Charsets.UTF_8))
                }
            }
        })
    }

    private fun handleControl(session: Session, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("t")) {
            "meta" -> {
                session.totalBytes = json.optLong("size")
                session.recvStartedAt = SystemClock.elapsedRealtime()
            }

            "duplex_switch" -> {
                // 对端推完第一程 → 本端开始推第二程
                if (!session.isOfferer) pushChunkBurst(session)
            }

            "duplex_done" -> {
                // 对端推完第二程 → 本端（offerer）全部结束
                if (session.isOfferer) {
                    session.peerPushDone = true
                    complete(session)
                }
            }
        }
    }

    /**
     * 推送一程数据（duplex 时序，与桌面端一致）：
     *  - offerer：推完发 `duplex_switch`，然后等对端回 `duplex_done` 才结束；
     *  - answerer：收到 `duplex_switch` 后推第二程，推完发 `duplex_done` 并结束。
     * 数据源：若 [Session.readChunk] 存在则用之（真实文件），否则用随机数据（测速）。
     */
    private fun pushChunkBurst(session: Session) {
        val total = session.totalBytes
        if (total <= 0L) return
        session.pushed = 0L
        session.pushStartedAt = SystemClock.elapsedRealtime()

        scope.launch {
            val totalChunks = ((total + session.chunkSize - 1) / session.chunkSize).toInt()
            val meta = JSONObject()
                .put("t", "meta")
                .put("size", total)
                .put("totalChunks", totalChunks)
                .put("chunkSize", session.chunkSize)
            sendText(session, meta.toString())

            val reader = session.readChunk
            for (i in 0 until totalChunks) {
                val start = i * session.chunkSize
                val len = minOf(session.chunkSize, (total - start).toInt()).coerceAtLeast(1)
                val data = try {
                    reader?.invoke(i) ?: Random.nextBytes(ByteArray(len))
                } catch (e: Exception) {
                    fail(session, "读取数据失败：${e.message}")
                    return@launch
                }
                waitForBuffer(session)
                val frame = ByteBuffer.allocate(4 + data.size).order(ByteOrder.BIG_ENDIAN)
                frame.putInt(i)
                frame.put(data)
                frame.flip()
                val ok = session.dc?.send(DataChannel.Buffer(frame, true)) ?: false
                if (!ok) {
                    fail(session, "发送失败（连接已断开）")
                    return@launch
                }
                session.pushed += data.size
                onProgressTick(session)
            }
            session.pushDone = true
            if (session.isOfferer) {
                sendText(session, JSONObject().put("t", "duplex_switch").toString())
                // 等对端推完后回 duplex_done（见 handleControl）
            } else {
                sendText(session, JSONObject().put("t", "duplex_done").toString())
                complete(session)
            }
        }
    }

    private suspend fun waitForBuffer(session: Session) {
        while ((session.dc?.bufferedAmount() ?: 0L) > BACKPRESSURE_BYTES) {
            delay(20)
        }
    }

    private fun sendText(session: Session, text: String) {
        runCatching {
            session.dc?.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray()), false))
        }
    }

    private fun onProgressTick(session: Session) {
        val elapsed = SystemClock.elapsedRealtime() - session.recvStartedAt
        val speed = if (elapsed > 0) session.received * 1000 / elapsed else 0
        _progress.value = _progress.value.copy(
            transferredBytes = maxOf(session.pushed, session.received),
            totalBytes = session.totalBytes,
            speedBps = speed,
            peerId = session.peerId,
        )
        session.onProgress?.invoke(session.received, session.totalBytes)
    }

    private fun complete(session: Session) {
        if (session.finished) return
        session.finished = true
        val elapsed = (SystemClock.elapsedRealtime() - session.pushStartedAt).coerceAtLeast(1)
        val speed = session.pushed * 1000 / elapsed
        val result = P2PResult(
            ok = true,
            ms = elapsed,
            speedBps = speed,
            bytes = session.pushed,
        )
        session.onResult?.invoke(result)
        closeSession(session.key)
        _progress.value = P2PProgress()
    }

    private fun fail(session: Session, reason: String) {
        if (session.finished) return
        session.finished = true
        session.onResult?.invoke(P2PResult(false, 0, 0, 0, reason))
        closeSession(session.key)
        _progress.value = P2PProgress()
    }

    private fun sdpObserver(session: Session) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {
            desc ?: return
            session.pc?.setLocalDescription(this, desc)
        }

        override fun onSetSuccess() {
            if (!session.localDescriptionSet) {
                session.localDescriptionSet = true
                if (session.isOfferer) {
                    val sdp = session.pc?.localDescription?.description ?: return
                    emit(session, "peer:offer", JSONObject().put("sdp", desc("offer", sdp)))
                } else if (!session.answerSent) {
                    session.answerSent = true
                    session.pc?.createAnswer(this, MediaConstraints())
                }
                return
            }
            // answerer 的 answer 已 setLocal → 发 peer:answer
            if (!session.isOfferer && session.answerSent) {
                val sdp = session.pc?.localDescription?.description ?: return
                emit(session, "peer:answer", JSONObject().put("sdp", desc("answer", sdp)))
            }
        }

        override fun onCreateFailure(error: String?) = fail(session, "创建 SDP 失败：$error")
        override fun onSetFailure(error: String?) = fail(session, "设置 SDP 失败：$error")
    }

    private fun desc(type: String, sdp: String) = JSONObject().put("type", type).put("sdp", sdp)

    private fun emit(session: Session, type: String, payload: JSONObject) {
        payload.put("tag", session.tag)
        sendSignal?.invoke(type, session.peerId, payload)
    }

    private fun observer(session: Session) = object : PeerConnection.Observer {
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
            emit(
                session,
                "peer:ice",
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

                PeerConnection.IceConnectionState.FAILED ->
                    fail(session, "打洞失败（对称 NAT？无 TURN 时需回退中转）")

                else -> Unit
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                _progress.value = _progress.value.copy(connected = true)
            }
        }

        override fun onDataChannel(channel: DataChannel?) {
            channel ?: return
            wireChannel(session, channel)
        }
    }
}
