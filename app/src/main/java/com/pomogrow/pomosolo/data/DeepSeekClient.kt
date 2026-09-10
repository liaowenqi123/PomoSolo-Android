package com.pomogrow.pomosolo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 选片客户端（对齐桌面端 `src-tauri/src/modules/downloader.rs` 的 `deepseek_select`）。
 *
 * 请求：`POST https://api.deepseek.com/chat/completions`，
 * `Authorization: Bearer <key>`，`model: deepseek-chat`，`temperature: 0`。
 * 只取前 6 条候选，模型只回一个数字 1-6 或 `None`。
 */
object DeepSeekClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

    /** 与桌面端 DEEPSEEK_SYSTEM_PROMPT 完全一致（原文照抄，勿改语义）。 */
    private const val SYSTEM_PROMPT = """你是纯音乐视频判断器。我会给你6个B站视频标题，你需要选出最像是纯音乐/原版音乐的视频编号。

判断标准：
**优先选择**（是纯音乐/原版）：
- 标题包含"无损"、"Hi-Res"、"FLAC"、"24bit"、"[音乐]"、"纯享版"、"官方"等
- "百万录音棚"系列
- 只有音乐和画面，没有额外解说或人声干扰
- 在其他条件相当时，优先选择非MV版本（纯音乐音频源），因为MV可能含有对白、环境音、场景音效等非音乐内容；但如果其它选项明显不符（如AI翻唱、教学、倍速修改等），MV版本也可以选择

**排除**（不是纯音乐或者出现修改或者出现重复循环）：
- 标题包含"AI翻唱"、"教程"、"钢琴教学"、"吉他教学"、"cover"、"翻唱"、"反应"、"解说"
- 有升key、降key、倍速修改（如"1.5倍速"、"+2key"）
- 有明显的人声互动或评论
- 单曲循环、单曲循环1h（可能循环多次）

**回复格式**：
- 只回复一个数字 1-6（最符合条件的视频编号）
- 如果没有一个是纯音乐，回复 None
- 不要有任何其他内容"""

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 从候选视频里挑一个最像纯音乐的。
     * @return 选中的索引（0-based）；`null` = 模型回答 None（没有合适的）；
     *         抛 [IOException] 表示调用失败（Key 无效 / 余额不足 / 网络异常…）。
     */
    suspend fun select(videos: List<BiliVideo>, apiKey: String): Int? = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext null
        val top = videos.take(6)
        val listText = top.mapIndexed { i, v -> "${i + 1}. ${v.title}" }.joinToString("\n")
        val userPrompt = "以下是6个视频标题，请选出最像纯音乐的一个：\n\n$listText"

        val content = chat(apiKey, SYSTEM_PROMPT, userPrompt, maxTokens = 16).trim()
        if (content.equals("none", ignoreCase = true)) return@withContext null

        val match = Regex("\\b([1-6])\\b").find(content) ?: return@withContext null
        val n = match.groupValues[1].toIntOrNull() ?: return@withContext null
        if (n in 1..top.size) n - 1 else null
    }

    /** 测试 Key 是否可用：返回模型的简短回显；失败抛 [IOException]。 */
    suspend fun test(apiKey: String): String = withContext(Dispatchers.IO) {
        chat(
            apiKey = apiKey,
            system = "你是连接测试助手。",
            user = "请只回复两个字：正常",
            maxTokens = 8,
        ).trim()
    }

    private fun chat(apiKey: String, system: String, user: String, maxTokens: Int): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("temperature", 0)
            .put("max_tokens", maxTokens)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(describeError(resp.code, text))
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                throw IOException("无法解析 DeepSeek 响应")
            }
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }

    private fun describeError(code: Int, body: String): String = when (code) {
        401 -> "API Key 无效或已过期（401）"
        402 -> "DeepSeek 账户余额不足（402）"
        422 -> "请求参数错误（422）"
        429 -> "请求过于频繁，请稍后再试（429）"
        500, 502, 503 -> "DeepSeek 服务暂时不可用（$code）"
        else -> "DeepSeek API 错误（$code）" + if (body.isNotBlank()) "：${body.take(120)}" else ""
    }
}
