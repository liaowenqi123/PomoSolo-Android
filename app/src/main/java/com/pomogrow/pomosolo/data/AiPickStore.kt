package com.pomogrow.pomosolo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 选片配置（对齐桌面端 charts.rs 里的 api_key 配置）。
 *
 * 桌面端 Key 有两种来源：本地手动填（存 data.json），或云端模式登录后由服务器
 * `GET /api/v1/config/deepseek-key` 下发。安卓端目前只做「本地手动填」，
 * 服务器下发留到账号体系（m4）时接。
 */
data class AiPickConfig(
    /** 是否启用 AI 选片（关闭则退化为本地规则打分）。 */
    val enabled: Boolean = true,
    /** DeepSeek API Key（空 = 未配置）。 */
    val apiKey: String = "",
    val model: String = "deepseek-chat",
) {
    val configured: Boolean get() = apiKey.isNotBlank()
}

object AiPickStore {

    private const val PREF = "pomodoro-ai-pick"

    private lateinit var prefs: SharedPreferences

    private val _config = MutableStateFlow(AiPickConfig())
    val config: StateFlow<AiPickConfig> = _config.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _config.value = AiPickConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "deepseek-chat").orEmpty().ifBlank { "deepseek-chat" },
        )
    }

    fun update(transform: (AiPickConfig) -> AiPickConfig) {
        val next = transform(_config.value)
        _config.value = next
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_API_KEY, next.apiKey)
            .putString(KEY_MODEL, next.model)
            .apply()
    }

    private const val KEY_ENABLED = "enabled"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_MODEL = "model"
}
