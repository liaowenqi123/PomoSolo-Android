package com.pomogrow.pomosolo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 在线用户（服务端 `p2p:online` 返回，camelCase）。 */
data class P2POnlineUser(val userId: String, val username: String)

/** 一条测试结论。 */
data class P2PTestRow(
    val name: String,
    val upMbps: Double,
    val downMbps: Double,
    val note: String,
)

/**
 * P2P 打洞测试工具（与桌面端 `P2PTestPanel.vue` 互通）。
 *
 * 协议（服务端只做定向转发，字段名必须一致）：
 *  - `p2p:online`（带 id，请求-响应）→ `{users:[{userId,username}]}`；
 *  - `p2p:test_request{to_user_id,tag}` → 对端收到后**自动挂起接收**（answerer）；
 *  - `p2p:reverse_test_request{to_user_id,tag}` → 对端作为 offerer 推一程；
 *  - `p2p:bidir_test_request{to_user_id,tag1,tag2}` → 对端 answerer(tag1) + offerer(tag2)；
 *  - `p2p:test_result{to_user_id,ok,ms,speed_bps,bytes,error?}` → 回传本端结果。
 *
 * 桌面端点"开始测试"会依次跑 A（正向）/ B（反向）/ AB（双向）三轮，安卓端对齐；
 * 反过来桌面端也能对安卓发起（本类会自动应答）。
 */
object P2PTestStore {

    private val _users = MutableStateFlow<List<P2POnlineUser>>(emptyList())
    val users: StateFlow<List<P2POnlineUser>> = _users.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _results = MutableStateFlow<List<P2PTestRow>>(emptyList())
    val results: StateFlow<List<P2PTestRow>> = _results.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------- 拉在线用户 ----------------

    fun refreshUsers() {
        StudyRoomStore.request("p2p:online", JSONObject()) { msg, err ->
            if (err != null) {
                _message.value = "获取在线用户失败：$err"
                return@request
            }
            val arr = msg?.optJSONArray("users") ?: JSONArray()
            val list = ArrayList<P2POnlineUser>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("userId")
                if (id.isNotBlank()) list.add(P2POnlineUser(id, o.optString("username")))
            }
            _users.value = list
            _message.value = if (list.isEmpty()) "暂无其他在线用户（需对方也登录并保持 WS 在线）" else null
        }
    }

    // ---------------- 发起测试（对齐桌面端 runTest：A/B/AB） ----------------

    fun runFullTest(user: P2POnlineUser) {
        if (_testing.value) return
        val rows = mutableListOf<P2PTestRow>()
        _testing.value = true
        _results.value = emptyList()

        // A：本机作 offerer，对端收到 p2p:test_request 后自动挂起接收
        _message.value = "测试 A：本机发起（本机 offerer）…"
        StudyRoomStore.sendCustom(
            "p2p:test_request",
            JSONObject().put("to_user_id", user.userId).put("tag", TAG_A),
        )
        P2PTransfer.startDuplexTest(user.userId, TAG_A) { result ->
            rows.add(toRow("A · 本机发起（Wi-Fi 上行）", result, up = true))
            _results.value = rows.toList()
            startStageB(user, rows)
        }
    }

    private fun startStageB(user: P2POnlineUser, rows: MutableList<P2PTestRow>) {
        _message.value = "测试 B：反向（对端 offerer）…"
        StudyRoomStore.sendCustom(
            "p2p:reverse_test_request",
            JSONObject().put("to_user_id", user.userId).put("tag", TAG_B),
        )
        // 本机作 answerer 挂起，等对端 offer
        P2PTransfer.receive(user.userId, TAG_B, timeoutMs = 15_000) { result ->
            rows.add(toRow("B · 对端发起（下行视角）", result, up = false))
            _results.value = rows.toList()
            _testing.value = false
            _message.value = "打洞测试完成（可对照桌面端结果显示）"
        }
    }

    private fun toRow(name: String, result: P2PResult, up: Boolean): P2PTestRow {
        val mbps = result.speedBps / 1024.0 / 1024.0 * 8
        val note = if (result.ok) {
            "%.1f MB / %d ms".format(result.bytes / 1024.0 / 1024.0, result.ms)
        } else {
            result.error ?: "失败"
        }
        return if (up) P2PTestRow(name, mbps, 0.0, note) else P2PTestRow(name, 0.0, mbps, note)
    }

    // ---------------- 被动应答（桌面端/其他安卓发起时） ----------------

    fun onWsMessage(msg: JSONObject) {
        val type = msg.optString("type")
        val from = msg.optString("from_user_id")
        val tag = msg.optString("tag")

        when (type) {
            "p2p:online" -> Unit // 走 request 回调

            "p2p:test_request" -> {
                // 对端请我当接收端：自动挂起等 offer（对齐桌面端 music.ts 的行为）
                _message.value = "${msg.optString("from_username", "对方")} 发起了打洞测试（本机接收）"
                P2PTransfer.receive(from, tag, timeoutMs = 12_000) { report(from, it) }
            }

            "p2p:reverse_test_request" -> {
                // 对端要我作为 offerer 推一程
                _message.value = "对端请求反向测试（本机发送）"
                P2PTransfer.startDuplexTest(from, tag) { report(from, it) }
            }

            "p2p:bidir_test_request" -> {
                val tag1 = msg.optString("tag1")
                val tag2 = msg.optString("tag2")
                _message.value = "对端请求双向测试"
                if (tag1.isNotBlank()) {
                    P2PTransfer.receive(from, tag1, timeoutMs = 12_000) { report(from, it) }
                }
                if (tag2.isNotBlank()) {
                    P2PTransfer.startDuplexTest(from, tag2) { report(from, it) }
                }
            }

            "p2p:test_result" -> {
                val ok = msg.optBoolean("ok")
                val mbps = msg.optLong("speed_bps") / 1024.0 / 1024.0 * 8
                _message.value = if (ok) {
                    "对端结果：%.1f Mbps · %d ms".format(mbps, msg.optLong("ms"))
                } else {
                    "对端结果：失败（${msg.optString("error").ifBlank { "未知原因" }}）"
                }
            }
        }
    }

    /** 把本端测速结果回传给发起方（字段名对齐服务端白名单）。 */
    private fun report(toUserId: String, result: P2PResult) {
        val payload = JSONObject()
            .put("to_user_id", toUserId)
            .put("ok", result.ok)
            .put("ms", result.ms)
            .put("speed_bps", result.speedBps)
            .put("bytes", result.bytes)
        if (!result.ok && result.error != null) payload.put("error", result.error)
        StudyRoomStore.sendCustom("p2p:test_result", payload)
    }

    private const val TAG_A = "a"
    private const val TAG_B = "b"
}
