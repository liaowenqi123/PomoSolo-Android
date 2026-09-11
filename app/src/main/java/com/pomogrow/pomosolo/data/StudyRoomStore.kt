package com.pomogrow.pomosolo.data

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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 自习室成员（服务端 `room:members` 的字段是 camelCase）。 */
data class RoomMember(val userId: String, val username: String, val online: Boolean)

/** 聊天消息。 */
data class ChatMessage(
    val userId: String,
    val username: String,
    val message: String,
    val time: String,
)

/** 公开房间（REST `GET /api/v1/rooms`）。 */
data class PublicRoom(
    val id: String,
    val name: String,
    val description: String,
    val memberCount: Int,
    val maxMembers: Int,
    val hasPassword: Boolean,
    val creatorName: String,
)

data class StudyRoomState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    /** 被服务器踢下线（同账号异地登录，关闭码 4001）——此时不再自动重连 */
    val kicked: Boolean = false,
    val rooms: List<PublicRoom> = emptyList(),
    val loadingRooms: Boolean = false,
    val roomId: String = "",
    val roomName: String = "",
    val members: List<RoomMember> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val djUserId: String = "",
    val djUsername: String = "",
    val message: String? = null,
    val error: String? = null,
) {
    val inRoom: Boolean get() = roomId.isNotEmpty()
}

/**
 * 自习室（WebSocket）。协议权威：主仓库 `server-planning/ws_server.py` 与
 * `EXTERNAL-INTERFACES.md` §3–§8；客户端行为对齐 PWA `src/pwa/ws.ts`：
 *
 *  - 连接 `wss://api.pomogrow.top/ws?token=<access_token>`（token 走 query）；
 *  - 带 `id` 的消息是请求-响应（8s 超时），不带 id 是纯广播；
 *  - 心跳每 10s 发 `ping`，服务器回 `pong`；
 *  - 断线指数退避重连（1s×2^n，上限 15s），重连前刷新 token；
 *  - 关闭码 **4001 = 同账号异地登录被踢**，不再重连（避免双端互踢死循环）；
 *  - 所有收到的消息原样交给 [onServerMessage]，再做业务分发。
 */
object StudyRoomStore {

    const val WS_URL = "wss://api.pomogrow.top/ws"

    private const val HEARTBEAT_MS = 10_000L
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val MAX_CHAT = 200

    /** 房间级心跳（对齐桌面端 HEARTBEAT_INTERVAL_MS = 5s）：ping{room_id} 保活。 */
    private const val ROOM_PING_MS = 5_000L

    /** 房间级状态同步（对齐桌面端 REFRESH_INTERVAL_MS = 15s）：presence:update。 */
    private const val PRESENCE_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket 长连接
            .build()
    }

    private val _state = MutableStateFlow(StudyRoomState())
    val state: StateFlow<StudyRoomState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var roomPingJob: Job? = null
    private var presenceJob: Job? = null
    private var attempt = 0
    private var idCounter = 1
    private val pending = mutableMapOf<Int, (JSONObject?, String?) -> Unit>()
    private var manualClosed = false

    /** join 响应回来之前先到的 room:members 快照（对齐桌面端 pendingMembers 缓存）。 */
    private var pendingMembers: List<RoomMember>? = null

    /** 自愈重进房间的节流时间戳。 */
    private var lastRejoinAt = 0L

    /** 收到的原始消息回调（供外部模块消费，例如同步听歌 / 传歌）。 */
    var onServerMessage: ((JSONObject) -> Unit)? = null

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---------------- 连接管理 ----------------

    fun connect() {
        if (socket != null) return
        val token = AuthStore.state.value.accessToken
        if (token.isBlank()) {
            fail("请先在「设置 → 账号」登录后再进入自习室")
            return
        }
        manualClosed = false
        _state.value = _state.value.copy(connecting = true, kicked = false, error = null)
        val url = "$WS_URL?token=" + URLEncoder.encode(token, "UTF-8")
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    fun disconnect() {
        manualClosed = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "leave")
        socket = null
        settleAll("连接已关闭")
        _state.value = _state.value.copy(connected = false, connecting = false)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            _state.value = _state.value.copy(connected = true, connecting = false, kicked = false)
            startHeartbeat()
            // 断线重连后自动回到原房间（对齐桌面端 autoReconnect）并恢复房间级定时器
            val roomId = _state.value.roomId
            if (roomId.isNotEmpty()) {
                send("room:join", JSONObject().put("room_id", roomId), withId = false)
                startRoomTimers(roomId)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = try {
                JSONObject(text)
            } catch (_: Exception) {
                return
            }
            // 请求-响应匹配
            if (msg.has("id")) {
                val id = msg.optInt("id", -1)
                val waiter = pending.remove(id)
                if (waiter != null) {
                    if (msg.optString("type") == "error") {
                        waiter(null, msg.optString("error").ifBlank { "服务器错误" })
                    } else {
                        waiter(msg, null)
                    }
                }
            }
            onServerMessage?.invoke(msg)
            handleMessage(msg)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onDisconnected(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onDisconnected(-1)
        }
    }

    private fun onDisconnected(code: Int) {
        if (socket != null) {
            heartbeatJob?.cancel()
            heartbeatJob = null
            socket = null
        }
        // 房间级定时器在断线期间没有意义；重连成功后 onOpen 会重新 join 并重启
        stopRoomTimers()
        settleAll("连接已断开")
        val kicked = code == 4001
        _state.value = _state.value.copy(
            connected = false,
            connecting = false,
            kicked = kicked,
            roomId = if (kicked) "" else _state.value.roomId,
            members = if (kicked) emptyList() else _state.value.members,
            error = if (kicked) "账号在其他设备登录，已退出自习室" else null,
        )
        if (manualClosed || kicked) return
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectJob != null) return
        val wait = minOf(15_000L, 1_000L * (1L shl minOf(attempt, 4)))
        attempt += 1
        reconnectJob = scope.launch {
            delay(wait)
            reconnectJob = null
            if (manualClosed || socket != null) return@launch
            // 重连前刷新 token（access 15 分钟过期，直接重连会被鉴权拒绝）
            AuthStore.refreshSession(silent = true)
            delay(400)
            connect()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                if (socket == null) break
                sendRaw(JSONObject().put("type", "ping"), withId = false)
            }
        }
    }

    // ---------------- 发送 ----------------

    private fun sendRaw(obj: JSONObject, withId: Boolean): Int? {
        val ws = socket ?: return null
        val id = if (withId) idCounter++ else null
        if (id != null) obj.put("id", id)
        return try {
            ws.send(obj.toString())
            id
        } catch (_: Exception) {
            null
        }
    }

    /** 发送消息；[withId] = true 时等待服务器同名 id 响应（8s 超时）。 */
    private fun send(
        type: String,
        payload: JSONObject = JSONObject(),
        withId: Boolean = true,
        onResult: ((JSONObject?, String?) -> Unit)? = null,
    ) {
        connectIfNeeded()
        val obj = JSONObject().put("type", type)
        val keys = payload.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            obj.put(k, payload.get(k))
        }
        val id = sendRaw(obj, withId)
        if (id == null) {
            onResult?.invoke(null, "未连接到服务器")
            return
        }
        if (!withId || onResult == null) return
        pending[id] = onResult
        scope.launch {
            delay(REQUEST_TIMEOUT_MS)
            val waiter = pending.remove(id)
            waiter?.invoke(null, "请求超时：$type")
        }
    }

    private fun connectIfNeeded() {
        if (socket == null && !manualClosed) connect()
    }

    private fun settleAll(reason: String) {
        val list = pending.values.toList()
        pending.clear()
        list.forEach { it(null, reason) }
    }

    // ---------------- 房间 REST ----------------

    fun loadRooms() {
        val token = AuthStore.state.value.accessToken
        if (token.isBlank()) {
            fail("请先在「设置 → 账号」登录")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(loadingRooms = true)
            try {
                val json = AuthStore.getJson("/rooms")
                val arr = json?.optJSONArray("rooms") ?: JSONArray()
                val rooms = ArrayList<PublicRoom>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    rooms.add(
                        PublicRoom(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            description = o.optString("description"),
                            memberCount = o.optInt("member_count", 0),
                            maxMembers = o.optInt("max_members", 0),
                            hasPassword = o.optBoolean("has_password", false),
                            creatorName = o.optString("creator_name"),
                        ),
                    )
                }
                _state.value = _state.value.copy(rooms = rooms, loadingRooms = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingRooms = false,
                    error = "获取房间列表失败：${e.message ?: "网络错误"}",
                )
            }
        }
    }

    // ---------------- 房间动作 ----------------

    fun createRoom(name: String, description: String, password: String, maxMembers: Int = 20) {
        if (name.isBlank()) {
            fail("请输入房间名称")
            return
        }
        val payload = JSONObject()
            .put("name", name.trim())
            .put("description", description.trim())
            .put("max_members", maxMembers)
        if (password.isNotBlank()) payload.put("password", password)
        send("room:create", payload) { msg, err ->
            if (err != null) {
                fail(err)
                return@send
            }
            val room = msg?.optJSONObject("room")
            enterRoom(
                roomId = room?.optString("id").orEmpty(),
                roomName = room?.optString("name") ?: name,
                note = "房间已创建",
            )
        }
    }

    fun joinRoom(roomId: String, password: String = "") {
        if (roomId.isBlank()) {
            fail("请输入房间 ID")
            return
        }
        val payload = JSONObject().put("room_id", roomId.trim())
        if (password.isNotBlank()) payload.put("password", password)
        send("room:join", payload) { _, err ->
            if (err != null) {
                fail(err)
                return@send
            }
            val room = _state.value.rooms.firstOrNull { it.id == roomId.trim() }
            enterRoom(
                roomId = roomId.trim(),
                roomName = room?.name ?: "自习室",
                note = "已加入房间",
            )
        }
    }

    /**
     * 进入房间视图：应用缓存的成员快照（join 时服务端已广播 room:members），
     * 否则乐观加入自己 —— 避免进房瞬间显示"空无一人"（对齐桌面端 enterRoom）。
     * 同时启动房间级定时器（5s ping 保活 + 15s presence:update 状态同步）。
     */
    private fun enterRoom(roomId: String, roomName: String, note: String) {
        if (roomId.isBlank()) {
            fail("服务器未返回房间 ID")
            return
        }
        val me = AuthStore.state.value.user
        val cached = pendingMembers
        val members = when {
            cached != null -> cached
            me != null -> listOf(RoomMember(me.id, me.username, true))
            else -> emptyList()
        }
        pendingMembers = null
        _state.value = _state.value.copy(
            roomId = roomId,
            roomName = roomName,
            chat = emptyList(),
            members = members,
            message = note,
        )
        // 进房立刻同步一次在线状态（服务端会广播 room:member_status）
        send(
            "presence:update",
            JSONObject().put("status", "idle").put("room_id", roomId),
            withId = false,
        )
        startRoomTimers(roomId)
    }

    /**
     * 房间级保活/同步定时器（对齐桌面端 StudyRoom.vue）：
     *  - 5s：`ping{room_id}`（study_room_update_status）—— 高频保活，防代理/NAT 掐断；
     *  - 15s：`presence:update{status, room_id}`（study_room_get_members 的触发方式）
     *    —— 服务端据此广播 room:member_status，成员在线状态保持新鲜。
     */
    private fun startRoomTimers(roomId: String) {
        stopRoomTimers()
        roomPingJob = scope.launch {
            while (true) {
                delay(ROOM_PING_MS)
                if (_state.value.roomId != roomId) break
                send("ping", JSONObject().put("room_id", roomId), withId = false)
            }
        }
        presenceJob = scope.launch {
            while (true) {
                delay(PRESENCE_MS)
                if (_state.value.roomId != roomId) break
                send(
                    "presence:update",
                    JSONObject().put("status", "idle").put("room_id", roomId),
                    withId = false,
                )
            }
        }
    }

    private fun stopRoomTimers() {
        roomPingJob?.cancel()
        roomPingJob = null
        presenceJob?.cancel()
        presenceJob = null
    }

    /** 自愈：收到成员快照却发现自己不在其中 → 重新 join（5s 节流，防抖）。 */
    private fun rejoinIfMissing(members: List<RoomMember>) {
        val me = AuthStore.state.value.user?.id ?: return
        if (members.isEmpty() || members.any { it.userId == me }) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRejoinAt < 5_000L) return
        lastRejoinAt = now
        send("room:join", JSONObject().put("room_id", _state.value.roomId), withId = false)
    }

    fun leaveRoom() {
        val id = _state.value.roomId
        if (id.isNotBlank()) {
            send(
                "room:leave",
                JSONObject().put("room_id", id),
                withId = false,
            )
        }
        stopRoomTimers()
        pendingMembers = null
        _state.value = _state.value.copy(
            roomId = "",
            roomName = "",
            members = emptyList(),
            chat = emptyList(),
            djUserId = "",
            djUsername = "",
        )
    }

    fun sendChat(text: String) {
        if (text.isBlank() || _state.value.roomId.isBlank()) return
        send("room:chat", JSONObject().put("message", text.trim()), withId = false)
    }

    fun requestDj() {
        if (!_state.value.inRoom) return
        send("music:request_dj", withId = false)
    }

    /** 番茄钟阶段完成 → 广播给房间成员。 */
    fun broadcastPomoDone(mode: String, durationMinutes: Int) {
        if (!_state.value.inRoom) return
        send(
            "room:pomo_done",
            JSONObject()
                .put("room_id", _state.value.roomId)
                .put("mode", mode)
                .put("duration", durationMinutes),
            withId = false,
        )
    }

    /** DJ 广播播放状态（对齐 music:sync_state）。 */
    fun broadcastMusicState(songId: String, playing: Boolean, positionMs: Long) {
        if (!_state.value.inRoom) return
        if (_state.value.djUserId.isBlank()) return
        if (_state.value.djUserId != AuthStore.state.value.user?.id) return
        send(
            "music:sync_state",
            JSONObject()
                .put("song_id", songId)
                .put("playing", playing)
                .put("position_ms", positionMs)
                .put("volume", 100),
            withId = false,
        )
    }

    // ---------------- 消息分发 ----------------

    private fun handleMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "room:created", "room:joined" -> Unit // 已在回调里处理

            "room:members" -> {
                val arr = msg.optJSONArray("members") ?: JSONArray()
                val list = ArrayList<RoomMember>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        RoomMember(
                            userId = o.optString("userId").ifBlank { o.optString("user_id") },
                            username = o.optString("username"),
                            online = o.optBoolean("online", true),
                        ),
                    )
                }
                if (_state.value.roomId.isEmpty()) {
                    // join 响应还没回来：先缓存，进房时应用
                    //（否则会被 join 回调的初始化覆盖成空列表 → 人数显示为 0）
                    pendingMembers = list
                } else {
                    _state.value = _state.value.copy(members = list)
                    rejoinIfMissing(list)
                }
            }

            "room:member_joined" -> {
                val user = msg.optJSONObject("user") ?: return
                val id = user.optString("id")
                if (_state.value.members.none { it.userId == id }) {
                    _state.value = _state.value.copy(
                        members = _state.value.members + RoomMember(
                            userId = id,
                            username = user.optString("username"),
                            online = true,
                        ),
                    )
                }
            }

            "room:member_left" -> {
                val id = msg.optString("user_id")
                _state.value = _state.value.copy(
                    members = _state.value.members.filterNot { it.userId == id },
                )
            }

            "room:member_status" -> {
                val id = msg.optString("user_id")
                val status = msg.optString("status")
                _state.value = _state.value.copy(
                    members = _state.value.members.map {
                        if (it.userId == id) it.copy(online = status != "offline") else it
                    },
                )
            }

            "room:chat" -> {
                val chat = ChatMessage(
                    userId = msg.optString("user_id"),
                    username = msg.optString("username"),
                    message = msg.optString("message"),
                    time = normalizeTime(msg.opt("time")),
                )
                val list = (_state.value.chat + chat).takeLast(MAX_CHAT)
                _state.value = _state.value.copy(chat = list)
            }

            "room:pomo_done" -> {
                val who = msg.optString("username", "有人")
                _state.value = _state.value.copy(message = "$who 完成了一个番茄 🍅")
            }

            "music:dj_changed" -> {
                _state.value = _state.value.copy(
                    djUserId = msg.optString("dj_user_id"),
                    djUsername = msg.optString("dj_username"),
                )
            }

            "room:closed" -> {
                _state.value = _state.value.copy(
                    roomId = "",
                    roomName = "",
                    members = emptyList(),
                    chat = emptyList(),
                    error = "房间已被解散",
                )
            }

            "error" -> {
                val text = msg.optString("error")
                if (text.isNotBlank()) fail(text)
            }

            "pong" -> Unit
        }
    }

    private fun normalizeTime(raw: Any?): String = when (raw) {
        null -> ""
        is String -> raw
        is Number -> raw.toLong().let { ts ->
            if (ts > 1_000_000_000_000L) {
                android.text.format.DateFormat.format("HH:mm", ts).toString()
            } else {
                android.text.format.DateFormat.format("HH:mm", ts * 1000L).toString()
            }
        }
        else -> ""
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    /** 供外部（同步听歌 / 传歌）使用的请求发送入口。 */
    fun sendCustom(type: String, payload: JSONObject, withId: Boolean = false) {
        send(type, payload, withId)
    }

    fun markSynced(songId: String, playing: Boolean, positionMs: Long) {
        lastRemoteAt = SystemClock.elapsedRealtime()
        lastRemoteSongId = songId
        lastRemotePlaying = playing
        lastRemotePositionMs = positionMs
    }

    var lastRemoteAt: Long = 0
        private set
    var lastRemoteSongId: String = ""
        private set
    var lastRemotePlaying: Boolean = false
        private set
    var lastRemotePositionMs: Long = 0
        private set
}
