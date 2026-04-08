package com.holderzone.widget.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.holderzone.widget.theme.ScreenConfigInfo.heightFactor
import com.holderzone.widget.theme.ScreenConfigInfo.scale
import com.holderzone.widget.theme.ScreenConfigInfo.widthFactor

object ScreenConfigInfo {
    //高度比例
    var heightFactor = 0f

    //屏幕密度
    var scale = 0f

    //宽度比例
    var widthFactor = 0f


    /**
     * 初始化
     * @param designHeightDp 设计稿画布高度
     * @param designWidthDp 设计稿画布宽度
     */
    @Composable
    fun InitScreenConfigInfo(designWidthDp: Float = 1280f, designHeightDp: Float = 720f) {
        val config = LocalConfiguration.current
        val widthDp = config.screenWidthDp.toFloat()
        val heightDp = config.screenHeightDp.toFloat()

        scale = config.densityDpi / 160f

        if (heightFactor == 0f) heightFactor = heightDp / designHeightDp
        if (widthFactor == 0f) widthFactor = widthDp / designWidthDp

    }
}

fun Int.dp2px(): Int {
    return (this * scale + 0.5f).toInt()
}

fun Dp.dp2px(): Int {
    return (this.value * scale + 0.5f).toInt()
}


@Stable
inline val Int.wdp: Dp
    get() {
        val result = this.toFloat() * widthFactor
        return Dp(value = result)
    }

@Stable
inline val Float.wdp: Dp
    get() {
        val result = this * widthFactor
        return Dp(value = result)
    }


@Stable
inline val Int.hdp: Dp
    get() {
        val result = this.toFloat() * heightFactor
        return Dp(value = result)
    }

@Stable
inline val Float.hdp: Dp
    get() {
        val result = this * heightFactor
        return Dp(value = result)
    }

@Stable
inline val Int.spi: TextUnit
    get() {
        return this * heightFactor.sp
    }

@Stable
inline val Float.spi: TextUnit
    get() {
        return this * heightFactor.sp
    }


