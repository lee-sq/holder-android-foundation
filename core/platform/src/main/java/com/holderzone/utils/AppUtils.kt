package com.holderzone.utils

import android.content.Context
import com.blankj.utilcode.util.ProcessUtils

object AppUtils {
    /**
     * 获取版本号
     */
    fun gerVersionName(context: Context): String? {
        return try {
            val manager = context.packageManager
            val info = manager.getPackageInfo(context.packageName, 0)
            info.versionName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取VersionCode
     */
    fun getVersionCode(context: Context): Int {
        return try {
            val manager = context.packageManager
            val info = manager.getPackageInfo(context.packageName, 0)
            info.versionCode
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    /**
     * versionCode 一定是六位数，XX.XX.XX
     */
    fun generatorVersionName(versionCode: Int): String {
        if (versionCode !in 0..999999) {
            throw IllegalArgumentException("versionCode 必须是 0-999999 之间的六位数")
        }

        val major = versionCode / 10000
        val minor = (versionCode % 10000) / 100
        val patch = versionCode % 100

        return "$major.$minor.$patch"
    }

    fun getCurrentProcessName() = ProcessUtils.getCurrentProcessName()
}