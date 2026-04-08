package com.holderzone.utils

import com.blankj.utilcode.util.Utils

/**
 * 转换工具类
 * @author terry
 * @date 2018/08/22 下午11:59
 */
object HolderConvertUtils {

    fun dp2px(dpValue: Float): Int {
        val scale = Utils.getApp().applicationContext.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    fun px2dp(pxValue: Float): Int {
        val scale = Utils.getApp().applicationContext.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    fun sp2px(spValue: Float): Int {
        val fontScale = Utils.getApp().applicationContext.resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    fun px2sp(pxValue: Float): Int {
        val fontScale = Utils.getApp().applicationContext.resources.displayMetrics.scaledDensity
        return (pxValue / fontScale + 0.5f).toInt()
    }
}