package com.pomogrow.pomosolo.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 专注统计（对齐 PWA 的 stats store：todayCount / totalMinutes）。
 * 本地持久化；跨天自动重置「今日完成」。
 */
object StatsStore {

    private const val PREF = "pomodoro-stats"
    private const val KEY_DATE = "date"
    private const val KEY_TODAY = "todayCount"
    private const val KEY_TOTAL = "totalMinutes"

    private lateinit var prefs: SharedPreferences

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    private val _totalMinutes = MutableStateFlow(0)
    val totalMinutes: StateFlow<Int> = _totalMinutes.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        rollIfNeeded()
        _totalMinutes.value = prefs.getInt(KEY_TOTAL, 0)
    }

    /** 记录一次完成的专注（仅「专注」阶段完成时调用）。 */
    fun recordSession(minutes: Int) {
        rollIfNeeded()
        _todayCount.value += 1
        _totalMinutes.value += minutes.coerceAtLeast(0)
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putInt(KEY_TODAY, _todayCount.value)
            .putInt(KEY_TOTAL, _totalMinutes.value)
            .apply()
    }

    /** 手动清空今日数据（设置页用）。 */
    fun resetToday() {
        rollIfNeeded()
        _todayCount.value = 0
        if (!::prefs.isInitialized) return
        prefs.edit().putInt(KEY_TODAY, 0).apply()
    }

    private fun rollIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val saved = if (::prefs.isInitialized) prefs.getString(KEY_DATE, "") else today
        if (saved != today) {
            _todayCount.value = 0
            if (::prefs.isInitialized) {
                prefs.edit().putString(KEY_DATE, today).putInt(KEY_TODAY, 0).apply()
            }
        } else if (::prefs.isInitialized) {
            _todayCount.value = prefs.getInt(KEY_TODAY, 0)
        }
    }
}
