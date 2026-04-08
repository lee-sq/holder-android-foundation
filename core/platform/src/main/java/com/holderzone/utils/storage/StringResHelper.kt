package com.holderzone.utils.storage

import android.app.Application
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes


/**
 * 全局字符串资源管理器
 * 使用方法：
 * 1. 在 Application.onCreate() 中初始化：StringRes.init(this)
 * 2. 在任何地方使用：StringRes.getString(R.string.xxx)
 * 3. 在 ViewModel 中使用扩展函数：getString(R.string.xxx)
 */
object StringResHelper {

    private lateinit var application: Application

    /**
     * 初始化字符串资源管理器
     * 在 Application.onCreate() 中调用
     */
    fun init(application: Application) {
        this.application = application
    }

    /**
     * 获取字符串资源
     */
    fun getString(@StringRes resId: Int): String {
        return application.getString(resId)
    }

    /**
     * 获取格式化字符串资源
     */
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return application.getString(resId, *formatArgs)
    }

    /**
     * 获取字符串数组资源
     */
    fun getStringArray(@ArrayRes resId: Int): Array<String> {
        return application.resources.getStringArray(resId)
    }

    /**
     * 获取复数字符串资源
     */
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int): String {
        return application.resources.getQuantityString(resId, quantity)
    }

    /**
     * 获取格式化复数字符串资源
     */
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return application.resources.getQuantityString(resId, quantity, *formatArgs)
    }
}