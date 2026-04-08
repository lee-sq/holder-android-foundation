package com.holderzone.utils.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 时间相关的Util
 */
object TimeUtils {

    /**
     * 计算在指定时区下，targetTime + hour 距离当前时间的毫秒差。
     * @param targetTimeMillis 目标时间（Epoch 毫秒）
     * @param hour 在目标时间上叠加的小时数（可为负）
     * @param timeZoneId 时区 ID，默认 "Asia/Shanghai"
     * @return 毫秒差：正数表示“还剩多久”，负数表示“已过去多久”
     */
    fun calculateDiffTime(
        targetTimeMillis: Long,
        hour: Int,
        timeZoneId: String = "Asia/Shanghai"
    ): Long {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val calendar = Calendar.getInstance(tz)
        calendar.timeInMillis = targetTimeMillis
        calendar.add(Calendar.HOUR_OF_DAY, hour)

        val targetMillis = calendar.timeInMillis
        val nowMillis = System.currentTimeMillis()
        return targetMillis - nowMillis
    }

    /**
     * 判断是否已过期（<= 0）。
     */
    fun isExpired(
        targetTimeMillis: Long,
        hour: Int,
        timeZoneId: String = "Asia/Shanghai"
    ): Boolean {
        return calculateDiffTime(targetTimeMillis, hour, timeZoneId) <= 0L
    }


    /**
     * 将毫秒值格式化为中文倒计时：
     * - XX时XX分XX秒（按需省略零单位）
     * - 毫秒 < 0 返回空字符串
     */
    fun formatCountdownCn(millis: Long): String {
        if (millis < 0) return ""

        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            buildString {
                append("${hours}时")
                if (minutes > 0) append("${minutes}分")
                if (seconds > 0) append("${seconds}秒")
            }.ifEmpty { "0秒" } // 理论上不会为空，兜底
        } else {
            if (minutes > 0) {
                if (seconds > 0) "${minutes}分${seconds}秒" else "${minutes}分"
            } else {
                "${seconds}秒"
            }
        }
    }

    fun formatTimestamp(pattern: String, timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val formate = SimpleDateFormat(pattern, Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val date = Date(timestamp)
        return formate.format(date)
    }
}