package com.holderzone.utils

/**
 * 将字符串按固定长度拆分
 * @param length 每段字符串的长度
 * @return 拆分后的字符串列表
 */
fun String.splitByLength(length: Int): List<String> {
    if (this.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var start = 0
    while (start < this.length) {
        val end = (start + length).coerceAtMost(this.length)
        result.add(this.substring(start, end))
        start = end
    }
    return result
}