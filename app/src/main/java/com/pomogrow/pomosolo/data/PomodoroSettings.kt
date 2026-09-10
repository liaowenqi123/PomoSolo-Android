package com.pomogrow.pomosolo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 番茄钟设置（对齐 PWA 的 AppSettings 中与计时/提醒相关字段，并补安卓原生项）。
 *
 * 注意：「运行中禁止暂停/重置」不是设置项，而是 PWA 的**专注模式开关**（专注页上的
 * FocusModeSwitch）—— 只有开启专注模式时运行中才不可暂停，重置等于中断专注。
 */
data class PomodoroSettings(
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val planRounds: Int = 4,
    val autoStartNext: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** 正向计时：超过 1 分钟才计入统计（PWA 文案：💡 超过1分钟才会计入统计）。 */
    val stopwatchCountOverOneMinute: Boolean = true,
)

object SettingsStore {

    private const val PREF = "pomodoro-settings"

    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(PomodoroSettings())
    val settings: StateFlow<PomodoroSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _settings.value = PomodoroSettings(
            workMinutes = prefs.getInt(KEY_WORK, 25),
            breakMinutes = prefs.getInt(KEY_BREAK, 5),
            planRounds = prefs.getInt(KEY_PLAN_ROUNDS, 4),
            autoStartNext = prefs.getBoolean(KEY_AUTO_NEXT, false),
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATE, true),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            stopwatchCountOverOneMinute = prefs.getBoolean(KEY_SW_ONE_MIN, true),
        )
    }

    fun update(transform: (PomodoroSettings) -> PomodoroSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putInt(KEY_WORK, next.workMinutes)
            .putInt(KEY_BREAK, next.breakMinutes)
            .putInt(KEY_PLAN_ROUNDS, next.planRounds)
            .putBoolean(KEY_AUTO_NEXT, next.autoStartNext)
            .putBoolean(KEY_SOUND, next.soundEnabled)
            .putBoolean(KEY_VIBRATE, next.vibrationEnabled)
            .putBoolean(KEY_KEEP_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_SW_ONE_MIN, next.stopwatchCountOverOneMinute)
            .apply()
    }

    private const val KEY_WORK = "workMinutes"
    private const val KEY_BREAK = "breakMinutes"
    private const val KEY_PLAN_ROUNDS = "planRounds"
    private const val KEY_AUTO_NEXT = "autoStartNext"
    private const val KEY_SOUND = "soundEnabled"
    private const val KEY_VIBRATE = "vibrationEnabled"
    private const val KEY_KEEP_SCREEN_ON = "keepScreenOn"
    private const val KEY_SW_ONE_MIN = "stopwatchCountOverOneMinute"
}
