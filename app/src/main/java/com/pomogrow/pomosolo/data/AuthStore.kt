package com.pomogrow.pomosolo.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 已登录用户（对齐 `cloud_auth.rs` 的 UserInfo 与 PWA 的 Session）。 */
data class AuthUser(
    val id: String,
    val username: String,
    val email: String = "",
    val nickname: String = "",
    val admin: Boolean = false,
)

data class AuthState(
    val busy: Boolean = false,
    val user: AuthUser? = null,
    val accessToken: String = "",
    val refreshToken: String = "",
    /** 连接测试结果：null = 尚未测试 */
    val serverOk: Boolean? = null,
    val latencyMs: Long = 0L,
    val message: String? = null,
    val error: String? = null,
) {
    val loggedIn: Boolean get() = user != null
}

/**
 * 账号体系（云认证），对接自建服务器 REST API。
 *
 * 接口权威文档：主仓库 `server-planning/EXTERNAL-INTERFACES.md` / `API-implementation.md`；
 * 客户端行为对齐桌面端 `src-tauri/src/commands/cloud_auth.rs` 与 PWA `src/pwa/http.ts`：
 *  - `Authorization: Bearer <access_token>`；
 *  - access token 15 分钟、refresh token 30 天**滚动刷新**；
 *  - 请求遇到 401 → 自动刷新一次并重试原请求，刷新失败才登出（网络抖动不登出）；
 *  - 错误统一读 `error` 字段（兼容 `detail`）。
 *
 * 端点：POST /auth/register | /auth/login | /auth/refresh | /auth/logout、GET /auth/session、
 * GET /config/deepseek-key（仅 admin，用于下发 AI 选片的 Key）、GET /api/status（连接测试）。
 *
 * 凭据存储：SharedPreferences（应用私有目录）。桌面端额外用 AES 加密文件，
 * Android 侧私有目录本身受沙箱隔离，如需更强保护可后续换 EncryptedSharedPreferences。
 */
object AuthStore {

    const val API_ORIGIN = "https://api.pomogrow.top"
    const val API_BASE = "$API_ORIGIN/api/v1"

    private const val PREF = "pomodoro-auth"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null, 0, 0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var initialized = false

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val access = prefs.getString(KEY_ACCESS, "").orEmpty()
        val refresh = prefs.getString(KEY_REFRESH, "").orEmpty()
        val user = readUser()
        _state.value = AuthState(user = user, accessToken = access, refreshToken = refresh)
        // 有 token 但没有用户信息（例如上次没写全）→ 拉一次会话补齐
        if (user == null && access.isNotEmpty()) refreshSession(silent = true)
    }

    // ---------------- 对外动作 ----------------

    /** 连接测试：对齐桌面端/PWA，依次尝试 /api/status → /api/v1/health。 */
    fun testConnection() {
        if (_state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            val start = SystemClock.elapsedRealtime()
            var ok = false
            for (url in listOf("$API_ORIGIN/api/status", "$API_BASE/health")) {
                val code = withContext(Dispatchers.IO) {
                    try {
                        executeUrl(url, "GET", null, "")
                    } catch (_: Exception) {
                        null
                    }
                }?.code ?: -1
                if (code in 200..299) {
                    ok = true
                    break
                }
            }
            val ms = SystemClock.elapsedRealtime() - start
            _state.value = _state.value.copy(
                busy = false,
                serverOk = ok,
                latencyMs = ms,
                message = if (ok) "服务器连接正常（${ms}ms）" else null,
                error = if (ok) null else "无法连接服务器",
            )
        }
    }

    /** 登录（本地先校验用户名 ≥2 / 密码 ≥6，与桌面端一致）。 */
    fun login(username: String, password: String, silent: Boolean = false) {
        val name = username.trim()
        if (name.length < 2) {
            fail("用户名至少 2 个字符")
            return
        }
        if (password.length < 6) {
            fail("密码至少 6 个字符")
            return
        }
        if (_state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                val body = JSONObject().put("username", name).put("password", password)
                val json = call("/auth/login", "POST", body, auth = false)
                val access = json?.optString("access_token").orEmpty()
                if (access.isBlank()) throw IOException("服务器未返回令牌")
                val refresh = json?.optString("refresh_token").orEmpty()
                val user = parseUser(json?.optJSONObject("user"))
                persistSession(access, refresh, user)
                _state.value = _state.value.copy(
                    busy = false,
                    user = user,
                    accessToken = access,
                    refreshToken = refresh,
                    message = if (silent) null else "登录成功",
                )
                syncDeepSeekKeyIfAdmin()
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "登录失败")
            }
        }
    }

    /** 注册（两次密码需一致），成功后自动登录。 */
    fun register(username: String, password: String, confirm: String) {
        val name = username.trim()
        if (name.length < 2) {
            fail("用户名至少 2 个字符")
            return
        }
        if (password.length < 6) {
            fail("密码至少 6 个字符")
            return
        }
        if (password != confirm) {
            fail("两次输入的密码不一致")
            return
        }
        if (_state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                val body = JSONObject().put("username", name).put("password", password)
                call("/auth/register", "POST", body, auth = false)
                _state.value = _state.value.copy(busy = false, message = "注册成功，正在登录…")
                login(name, password, silent = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "注册失败")
            }
        }
    }

    /** 退出登录：通知服务器后清空本地会话（失败也照样清空）。 */
    fun logout() {
        if (_state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true)
            val refresh = _state.value.refreshToken
            try {
                val body = JSONObject().put("refresh_token", refresh)
                call("/auth/logout", "POST", body, auth = true)
            } catch (_: Exception) {
                // 服务器不可达不阻塞本地登出
            }
            clearSession()
            _state.value = _state.value.copy(busy = false, message = "已退出登录")
        }
    }

    /** 手动校验/刷新会话（GET /auth/session）。 */
    fun refreshSession(silent: Boolean = false) {
        if (_state.value.accessToken.isEmpty()) return
        scope.launch {
            if (!silent) _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                val json = call("/auth/session", "GET")
                val user = parseUser(json?.optJSONObject("user"))
                if (user != null) {
                    persistUser(user)
                    _state.value = _state.value.copy(user = user, busy = false, message = "会话有效")
                } else {
                    _state.value = _state.value.copy(busy = false, message = "会话有效")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "会话校验失败")
            }
        }
    }

    /**
     * admin 用户：从服务器拉取 DeepSeek Key 并写入 AI 选片配置
     * （对齐桌面端 `cloud_auth.rs::sync_deepseek_key`）。
     */
    fun syncDeepSeekKeyIfAdmin() {
        val user = _state.value.user ?: return
        if (!user.admin) return
        scope.launch {
            try {
                val json = call("/config/deepseek-key", "GET") ?: return@launch
                val raw = json.optString("api_key").orEmpty()
                val key = if (raw.equals("null", ignoreCase = true)) "" else raw
                if (key.isNotBlank()) {
                    AiPickStore.update { it.copy(apiKey = key, enabled = true, fromServer = true) }
                    _state.value = _state.value.copy(message = "已从服务器同步 DeepSeek Key")
                } else {
                    _state.value = _state.value.copy(message = "服务器未配置 DeepSeek Key")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "获取 DeepSeek Key 失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 手动清除服务器下发的 Key（切回本机配置）。 */
    fun clearServerDeepSeekKey() {
        AiPickStore.update { it.copy(fromServer = false) }
    }

    // ---------------- HTTP ----------------

    /**
     * 供其它模块复用的带鉴权 GET（自动带 Bearer、401 自动刷新重试）。
     * 例如自习室房间列表 `GET /api/v1/rooms`。
     */
    suspend fun getJson(path: String): JSONObject? = call(path, "GET")

    private class HttpResult(val code: Int, val body: String)

    private suspend fun call(
        path: String,
        method: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        allowRetry: Boolean = true,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val token = if (auth) _state.value.accessToken else ""
        val first = try {
            executeUrl(API_BASE + path, method, body, token)
        } catch (e: IOException) {
            throw e
        }

        if (first.code == 401 && auth && allowRetry && _state.value.refreshToken.isNotEmpty()) {
            if (tryRefresh()) {
                val retry = executeUrl(API_BASE + path, method, body, _state.value.accessToken)
                return@withContext handle(retry)
            }
            clearSession()
            throw IOException("登录已过期，请重新登录")
        }
        return@withContext handle(first)
    }

    private fun handle(res: HttpResult): JSONObject? {
        if (res.code in 200..299) {
            return if (res.body.isBlank()) JSONObject() else JSONObject(res.body)
        }
        val message = try {
            val j = JSONObject(res.body)
            j.optString("error").ifBlank { j.optString("detail") }
        } catch (_: Exception) {
            ""
        }
        throw IOException(
            when {
                message.isNotBlank() -> message
                res.code == 404 -> "接口不存在（404）"
                res.code >= 500 -> "服务器错误（${res.code}）"
                else -> "请求失败（HTTP ${res.code}）"
            },
        )
    }

    /** 同步执行一次请求；网络异常统一转成带中文说明的 IOException。 */
    private fun executeUrl(url: String, method: String, body: JSONObject?, token: String): HttpResult {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val reqBody = body?.toString()?.toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(reqBody ?: EMPTY_BODY)
            "PUT" -> builder.put(reqBody ?: EMPTY_BODY)
            "DELETE" -> builder.delete(reqBody)
            else -> builder.method(method, reqBody)
        }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                HttpResult(resp.code, resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            throw IOException("无法连接服务器：${e.message ?: "网络异常"}")
        }
    }

    /** 刷新 access token（滚动刷新 refresh token）。失败返回 false。 */
    private fun tryRefresh(): Boolean {
        val refresh = _state.value.refreshToken
        if (refresh.isEmpty()) return false
        return try {
            val body = JSONObject().put("refresh_token", refresh)
            val res = executeUrl("$API_BASE/auth/refresh", "POST", body, "")
            if (res.code !in 200..299) return false
            val json = JSONObject(res.body)
            val access = json.optString("access_token")
            if (access.isBlank()) return false
            val newRefresh = json.optString("refresh_token").ifBlank { refresh }
            persistToken(access, newRefresh)
            _state.value = _state.value.copy(accessToken = access, refreshToken = newRefresh)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseUser(obj: JSONObject?): AuthUser? {
        obj ?: return null
        val id = obj.optString("id")
        val username = obj.optString("username")
        if (id.isBlank() || username.isBlank()) return null
        return AuthUser(
            id = id,
            username = username,
            email = obj.optString("email").orEmpty(),
            nickname = obj.optString("nickname").orEmpty(),
            admin = obj.optBoolean("admin", false),
        )
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    // ---------------- 持久化 ----------------

    private fun persistSession(access: String, refresh: String, user: AuthUser?) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, user?.id.orEmpty())
            .putString(KEY_USERNAME, user?.username.orEmpty())
            .putString(KEY_EMAIL, user?.email.orEmpty())
            .putString(KEY_NICKNAME, user?.nickname.orEmpty())
            .putBoolean(KEY_ADMIN, user?.admin ?: false)
            .apply()
    }

    private fun persistToken(access: String, refresh: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
    }

    private fun persistUser(user: AuthUser) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NICKNAME, user.nickname)
            .putBoolean(KEY_ADMIN, user.admin)
            .apply()
    }

    private fun readUser(): AuthUser? {
        if (!::prefs.isInitialized) return null
        val id = prefs.getString(KEY_USER_ID, "").orEmpty()
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        if (id.isBlank() || username.isBlank()) return null
        return AuthUser(
            id = id,
            username = username,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            nickname = prefs.getString(KEY_NICKNAME, "").orEmpty(),
            admin = prefs.getBoolean(KEY_ADMIN, false),
        )
    }

    private fun clearSession() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
        _state.value = _state.value.copy(
            user = null,
            accessToken = "",
            refreshToken = "",
        )
    }

    private const val KEY_ACCESS = "accessToken"
    private const val KEY_REFRESH = "refreshToken"
    private const val KEY_USER_ID = "userId"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_ADMIN = "admin"
}
