package com.pomogrow.pomosolo.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 应用模式（对齐 PWA：single / plan / stopwatch）。 */
enum class AppMode(val label: String) {
    SINGLE("单次"),
    PLAN("计划"),
    STOPWATCH("正向"),
}

/** 计时模式（对齐 PWA：work / break）。 */
enum class TimerMode(val label: String) {
    WORK("专注"),
    BREAK("休息"),
}

enum class TimerPhase { READY, RUNNING, PAUSED, FINISHED }

/** 一次性事件：用于完成提醒（提示音 / 震动 / 文案）。 */
enum class TimerEvent { WORK_DONE, BREAK_DONE, PLAN_DONE }

data class TimerState(
    val appMode: AppMode = AppMode.SINGLE,
    val mode: TimerMode = TimerMode.WORK,
    val phase: TimerPhase = TimerPhase.READY,
    val totalMs: Long = 25 * 60_000L,
    val remainingMs: Long = 25 * 60_000L,
    val elapsedMs: Long = 0L,
    val planRound: Int = 1,
) {
    /** 圆环进度：1 - 剩余/总时长（PWA TimerProgress 同公式）。 */
    val progress: Float
        get() = when {
            appMode == AppMode.STOPWATCH -> 0f
            totalMs <= 0 -> 0f
            else -> (1f - remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
        }

    /** 圆环中央显示的时间。 */
    val displayMs: Long
        get() = if (appMode == AppMode.STOPWATCH) elapsedMs else remainingMs
}

/**
 * 番茄钟计时状态机（对齐 PWA timer store）。
 *
 * 计时基准用 [SystemClock.elapsedRealtime]，只按「结束时间戳」反推剩余时间，
 * 不依赖 tick 累加 —— 切后台/锁屏后回到前台时间依旧准确（PWA 用 200ms tick 累加，
 * 在浏览器里会因休眠漂移）。
 */
object PomodoroTimer {

    private const val TICK_MS = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    private var tickJob: Job? = null
    private var endAt = 0L
    private var startAt = 0L
    private var stopwatchBaseMs = 0L

    fun init(context: Context) {
        applySettings(SettingsStore.settings.value)
    }

    /** 设置变化时同步（仅未开始时改时长）。 */
    fun applySettings(s: PomodoroSettings) {
        val cur = _state.value
        if (cur.appMode == AppMode.STOPWATCH) return
        if (cur.phase == TimerPhase.RUNNING || cur.phase == TimerPhase.PAUSED) return
        val total = minutesOf(cur.mode, s) * 60_000L
        _state.value = cur.copy(totalMs = total, remainingMs = total)
    }

    fun setAppMode(appMode: AppMode) {
        stopTick()
        val s = SettingsStore.settings.value
        val cur = _state.value
        val total = minutesOf(cur.mode, s) * 60_000L
        _state.value = cur.copy(
            appMode = appMode,
            phase = TimerPhase.READY,
            totalMs = total,
            remainingMs = total,
            elapsedMs = 0L,
            planRound = 1,
        )
    }

    fun setMode(mode: TimerMode) {
        stopTick()
        val s = SettingsStore.settings.value
        val total = minutesOf(mode, s) * 60_000L
        _state.value = _state.value.copy(
            mode = mode,
            phase = TimerPhase.READY,
            totalMs = total,
            remainingMs = total,
        )
    }

    fun start() {
        val cur = _state.value
        if (cur.phase == TimerPhase.RUNNING) return
        val now = SystemClock.elapsedRealtime()
        if (cur.appMode == AppMode.STOPWATCH) {
            startAt = now
        } else {
            val remaining = if (cur.remainingMs <= 0L) cur.totalMs else cur.remainingMs
            endAt = now + remaining
        }
        _state.value = cur.copy(phase = TimerPhase.RUNNING)
        startTick()
    }

    fun pause() {
        val cur = _state.value
        if (cur.phase != TimerPhase.RUNNING) return
        val now = SystemClock.elapsedRealtime()
        stopTick()
        _state.value = if (cur.appMode == AppMode.STOPWATCH) {
            cur.copy(phase = TimerPhase.PAUSED, elapsedMs = stopwatchBaseMs + (now - startAt))
        } else {
            cur.copy(phase = TimerPhase.PAUSED, remainingMs = (endAt - now).coerceAtLeast(0L))
        }
    }

    fun toggle() {
        if (_state.value.phase == TimerPhase.RUNNING) pause() else start()
    }

    /** 重置；正向计时下等同于「结束并记录」。 */
    fun reset() {
        stopTick()
        val cur = _state.value
        val s = SettingsStore.settings.value
        if (cur.appMode == AppMode.STOPWATCH) {
            val minutes = (cur.elapsedMs / 60_000L).toInt()
            val threshold = if (s.stopwatchCountOverOneMinute) 1 else 0
            if (minutes > 0 && minutes >= threshold) {
                StatsStore.recordSession(minutes)
            }
        }
        val total = minutesOf(cur.mode, s) * 60_000L
        _state.value = cur.copy(
            phase = TimerPhase.READY,
            totalMs = total,
            remainingMs = total,
            elapsedMs = 0L,
            planRound = 1,
        )
    }

    /** 跳过当前阶段（不记录统计）。 */
    fun skip() {
        val cur = _state.value
        if (cur.appMode == AppMode.STOPWATCH) {
            reset()
            return
        }
        if (cur.phase == TimerPhase.READY) return
        advance(record = false)
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (true) {
                val cur = _state.value
                if (cur.phase != TimerPhase.RUNNING) break
                val now = SystemClock.elapsedRealtime()
                if (cur.appMode == AppMode.STOPWATCH) {
                    _state.value = cur.copy(elapsedMs = stopwatchBaseMs + (now - startAt))
                } else {
                    val remaining = (endAt - now).coerceAtLeast(0L)
                    _state.value = cur.copy(remainingMs = remaining)
                    if (remaining <= 0L) {
                        advance(record = true)
                        break
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTick() {
        val cur = _state.value
        if (cur.appMode == AppMode.STOPWATCH && cur.phase == TimerPhase.RUNNING) {
            stopwatchBaseMs = cur.elapsedMs
        }
        tickJob?.cancel()
        tickJob = null
    }

    private fun advance(record: Boolean) {
        stopTick()
        val cur = _state.value
        val s = SettingsStore.settings.value
        val nextMode = if (cur.mode == TimerMode.WORK) TimerMode.BREAK else TimerMode.WORK
        val nextRound = if (cur.mode == TimerMode.WORK) cur.planRound + 1 else cur.planRound

        if (record) {
            if (cur.mode == TimerMode.WORK) {
                StatsStore.recordSession(s.workMinutes)
                _events.tryEmit(TimerEvent.WORK_DONE)
            } else {
                _events.tryEmit(TimerEvent.BREAK_DONE)
            }
        }

        // 计划模式：完成设定轮数后收尾
        if (cur.appMode == AppMode.PLAN && nextRound > s.planRounds) {
            if (record) _events.tryEmit(TimerEvent.PLAN_DONE)
            val total = s.workMinutes * 60_000L
            _state.value = cur.copy(
                mode = TimerMode.WORK,
                phase = TimerPhase.READY,
                planRound = 1,
                totalMs = total,
                remainingMs = total,
                elapsedMs = 0L,
            )
            return
        }

        val total = minutesOf(nextMode, s) * 60_000L
        _state.value = cur.copy(
            mode = nextMode,
            phase = TimerPhase.READY,
            planRound = nextRound,
            totalMs = total,
            remainingMs = total,
            elapsedMs = 0L,
        )
        if (record && s.autoStartNext) start()
    }

    private fun minutesOf(mode: TimerMode, s: PomodoroSettings): Int =
        if (mode == TimerMode.WORK) s.workMinutes else s.breakMinutes
}
