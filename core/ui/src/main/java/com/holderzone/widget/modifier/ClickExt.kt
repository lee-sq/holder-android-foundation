package com.holderzone.widget.modifier

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 没有水波纹的点击事件，自带防抖
 */
fun Modifier.noIndicationClick(enable: Boolean = true, onClick: () -> Unit): Modifier = composed(
    factory = {
        Modifier.clickable(
            enabled = enable,
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            onClick()
        }
    }
)

fun Modifier.debounceNoIndicationClick(
    enable: Boolean = true,
    intervalMillis: Long = 600,
    onClick: () -> Unit
): Modifier =
    composed {
        var lastClickTs by remember { mutableLongStateOf(0L) }
        val lastestOnClick by rememberUpdatedState(onClick)
        Modifier.clickable(
            enabled = enable,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTs >= intervalMillis) {
                lastClickTs = now
                lastestOnClick()
            }
        }
    }

/**
 * @param pressColor 按下的颜色
 * @param backgroundColor 背景颜色
 * @param onItemClick 点击事件
 */
@Composable
fun Modifier.setPressColorAndBackground(
    pressColor: Color,
    backgroundColor: Color,
    onItemClick: (() -> Unit)? = null
): Modifier {
    val state = remember { mutableStateOf(false) }
    return this
        .background(color = if (state.value) pressColor else backgroundColor)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    state.value = true
                    if (tryAwaitRelease()) {
                        state.value = false
                        onItemClick?.invoke()
                    } else {
                        state.value = false
                    }
                }
            )
        }
}

fun Modifier.debounceClick(
    intervalMillis: Long = 300,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTs by remember { mutableLongStateOf(0L) }
    val latestOnClick by rememberUpdatedState(onClick)

    Modifier.clickable(
        enabled = enabled
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTs >= intervalMillis) {
            lastClickTs = now
            latestOnClick()
        }
    }
}

/**
 * 在 [thresholdMs] 毫秒后触发 [onLongPress]。
 * - 每次手势最多触发一次；
 * - [singleShot] 为 true：当前 Modifier 生命周期内只触发一次；
 * - 使用 Handler.postDelayer；通过 composed + onDispose 清理，避免泄漏。
 */
fun Modifier.longPressOnceInterop(
    thresholdMs: Long = 600,
    singleShot: Boolean = false,
    onLongPress: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val updatedOnLongPress = rememberUpdatedState(onLongPress)

    // 清理所有未执行的回调，避免组合退出后悬挂任务
    DisposableEffect(Unit) {
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    var globalTriggered = false
    var downX = 0f
    var downY = 0f
    val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (singleShot && globalTriggered) {
                    // 已触发过：不再调度，等待手势结束
                    return@pointerInteropFilter true
                }
                downX = event.x
                downY = event.y

                // 调度一次延迟执行
                handler.postDelayed({
                    updatedOnLongPress.value.invoke()
                    globalTriggered = true
                }, thresholdMs)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                // 超过触控阈值则取消
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    handler.removeCallbacksAndMessages(null)
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 抬起/取消且未触发：清理回调
                handler.removeCallbacksAndMessages(null)
                true
            }

            else -> false
        }
    }
}

fun Modifier.autoCloseKeyboard(): Modifier = composed {
    val keyboardController = LocalSoftwareKeyboardController.current
    pointerInput(this) {
        detectTapGestures(
            onPress = {
                keyboardController?.hide()
            }
        )
    }
}