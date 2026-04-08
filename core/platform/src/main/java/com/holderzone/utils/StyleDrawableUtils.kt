package com.holderzone.utils

import android.R
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt

class StyleDrawableUtils {
    companion object {
        var BASE_COLOR_PURPLE_ONE_NORMAL: Int = 0xF01E9C39.toInt()
        var BASE_COLOR_WHITE_ONE: Int = 0xffe8f0f1.toInt()

        fun getRoundStateListDrawable(
            cornerRadiusDp: Float,
            normalColor: Int,
            pressedColor: Int,
            disableColor: Int
        ): Drawable {
            var cornerRadius = HolderConvertUtils.dp2px(cornerRadiusDp).toFloat()
            return StateListDrawable().apply {
                addState(intArrayOf(-R.attr.state_enabled), GradientDrawable().apply {
                    setColor(disableColor)
                    this.cornerRadius = cornerRadius
                })
                addState(
                    intArrayOf(R.attr.state_enabled, -R.attr.state_pressed),
                    GradientDrawable().apply {
                        setColor(normalColor)
                        this.cornerRadius = cornerRadius
                    })
                addState(
                    intArrayOf(R.attr.state_enabled, R.attr.state_pressed),
                    GradientDrawable().apply {
                        setColor(pressedColor)
                        this.cornerRadius = cornerRadius
                    })
            }
        }

        fun getShape(@ColorInt bgColor: Int, cornerRadiusDp: Float) : Drawable {
           return  GradientDrawable().apply {
                setColor(bgColor)
                this.cornerRadius = HolderConvertUtils.dp2px(cornerRadiusDp).toFloat()
//                cornerRadii = floatArrayOf(
//                    size, size, size, size, 0f, 0f, 0f, 0f
//                )
            }
        }


        //选中桌台状态 动态设置
        var BASE_ROUND_PURPLE_DRAWABLE: Drawable? = null
            get() {
                return GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BASE_COLOR_PURPLE_ONE_NORMAL)
                    setSize(5, 5)
                    setStroke(2, BASE_COLOR_WHITE_ONE)
                }
            }

    }
}