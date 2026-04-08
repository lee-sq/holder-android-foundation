package com.holderzone.widget

/**
 * 通用倒计时组件（无样式）。
 * - 提供可记忆的 [CountdownTimerState] 控制器（开始/暂停/重置）。
 * - 通过 [CountdownTimer] 组合式入口渲染剩余时间，外观由调用方自定义。
 * - 时间单位均为毫秒。
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 倒计时状态控制器。
 * @param initialTotalMillis 总时长（毫秒）
 * @param intervalMillis 每次步进间隔（毫秒），默认 1000ms（每秒）
 * @param onTick 每次步进回调（传入剩余毫秒）
 * @param onFinished 结束回调（归零后触发一次）
 */
class CountdownTimerState internal constructor(
    initialTotalMillis: Long,
    private val intervalMillis: Long,
    private val onTick: ((Long) -> Unit)?,
    private val onFinished: (() -> Unit)?
) {
    var totalMillis by mutableLongStateOf(initialTotalMillis)
        private set
    var remainingMillis by mutableLongStateOf(initialTotalMillis)
        private set
    var isRunning by mutableStateOf(false)
        private set

    /** 启动倒计时（若已归零则从当前 [remainingMillis] 开始） */
    fun start() {
        isRunning = true
    }

    /** 暂停倒计时（保留当前剩余时间） */
    fun pause() {
        isRunning = false
    }

    /**
     * 重置倒计时。
     * @param newTotalMillis 新的总时长（不传则使用已有总时长）
     * @param autostart 是否在重置后立即开始
     */
    fun reset(newTotalMillis: Long? = null, autostart: Boolean = false) {
        if (newTotalMillis != null) {
            totalMillis = newTotalMillis
        }
        remainingMillis = totalMillis
        isRunning = autostart
    }

    /** 进行一次步进（内部调用），并在归零时触发 [onFinished] */
    internal fun tickOnce() {
        val next = max(0L, remainingMillis - intervalMillis)
        remainingMillis = next
        onTick?.invoke(next)
        if (next == 0L) {
            isRunning = false
            onFinished?.invoke()
        }
    }
}

/**
 * 记忆型倒计时控制器。
 * - 重组时保留状态；当 [totalMillis] 变化时会自动重置。
 * - [autoStart] 控制是否自动开始。
 */
@Composable
fun rememberCountdownTimer(
    totalMillis: Long,
    intervalMillis: Long = 1000L,
    autoStart: Boolean = true,
    onTick: ((Long) -> Unit)? = null,
    onFinished: (() -> Unit)? = null
): CountdownTimerState {
    val state = remember {
        CountdownTimerState(totalMillis, intervalMillis, onTick, onFinished)
    }
    LaunchedEffect(totalMillis) {
        state.reset(totalMillis, autoStart)
    }
    LaunchedEffect(state.isRunning, intervalMillis) {
        while (state.isRunning && state.remainingMillis > 0L) {
            delay(intervalMillis)
            state.tickOnce()
        }
    }
    return state
}

/**
 * 组合式倒计时入口。
 * - 不限定UI样式，调用方通过 [content] 渲染当前剩余时间。
 */
@Composable
fun CountdownTimer(
    totalMillis: Long,
    intervalMillis: Long = 1000L,
    autoStart: Boolean = true,
    onTick: ((Long) -> Unit)? = null,
    onFinished: (() -> Unit)? = null,
    content: @Composable (remainingMillis: Long) -> Unit
) {
    val state = rememberCountdownTimer(totalMillis, intervalMillis, autoStart, onTick, onFinished)
    content(state.remainingMillis)
}
