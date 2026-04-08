package com.holderzone.utils

import kotlinx.serialization.json.Json

object JsonUtils {
    // 全局 Json 配置（可调整）
    val defaultJson: Json = Json {
        ignoreUnknownKeys = true   // 解码时忽略未知字段
        isLenient = true           // 宽松解析（允许非严格 JSON）
        encodeDefaults = true      // 编码时包含默认值
        explicitNulls = false      // null 字段可省略
        prettyPrint = false        // 生产环境建议关闭美化
    }
}

// 任意 @Serializable 类型 → JSON 字符串
inline fun <reified T> T.toJson(json: Json = JsonUtils.defaultJson): String =
    json.encodeToString(this)

// JSON 字符串 → 指定类型对象
inline fun <reified T> String.fromJson(json: Json = JsonUtils.defaultJson): T =
    json.decodeFromString(this)

// 宽容版本：解析失败返回 null
inline fun <reified T> String.fromJsonOrNull(json: Json = JsonUtils.defaultJson): T? =
    runCatching { json.decodeFromString<T>(this) }.getOrNull()