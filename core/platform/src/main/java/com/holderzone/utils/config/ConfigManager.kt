package com.holderzone.utils.config

import com.holderzone.utils.storage.MMKVUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 响应式配置管理器
 * 提供实时的配置更新和监听功能
 *
 * 特性：
 * 1. 响应式配置更新 - 使用 StateFlow 实现
 * 2. 自动持久化 - 配置变更自动保存到 MMKV
 * 3. 类型安全 - 支持泛型配置类型
 * 4. 线程安全 - 支持多线程访问
 * 5. 内存缓存 - 避免频繁读取存储
 */
class ConfigManager<T : Any>(
    private val key: String,
    private val defaultValue: T,
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T
) {

    /**
     * 内部可变状态流
     */
    private val _configFlow = MutableStateFlow(loadConfig())

    /**
     * 对外暴露的只读状态流
     */
    val configFlow: StateFlow<T> = _configFlow.asStateFlow()

    /**
     * 当前配置值（同步获取）
     */
    val currentConfig: T
        get() = _configFlow.value

    /**
     * 从存储加载配置
     */
    private fun loadConfig(): T {
        return try {
            val configString = MMKVUtils.getString(key)
            if (configString.isEmpty()) {
                defaultValue
            } else {
                deserializer(configString)
            }
        } catch (e: Exception) {
            // 如果反序列化失败，返回默认值
            defaultValue
        }
    }

    /**
     * 更新配置
     * @param newConfig 新的配置对象
     */
    fun updateConfig(newConfig: T) {
        try {
            // 保存到存储
            val configString = serializer(newConfig)
            MMKVUtils.putString(key, configString)

            // 更新状态流
            _configFlow.value = newConfig
        } catch (e: Exception) {
            // 序列化失败时的处理
            throw ConfigUpdateException("Failed to update config: ${e.message}", e)
        }
    }

    /**
     * 更新配置（使用 lambda 表达式）
     * @param updater 配置更新函数
     */
    fun updateConfig(updater: (T) -> T) {
        val newConfig = updater(currentConfig)
        updateConfig(newConfig)
    }

    /**
     * 重新加载配置（从存储中强制刷新）
     */
    fun reloadConfig() {
        _configFlow.value = loadConfig()
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        updateConfig(defaultValue)
    }

    /**
     * 清除配置
     */
    fun clearConfig() {
        MMKVUtils.remove(key)
        _configFlow.value = defaultValue
    }

    companion object {
        /**
         * 创建支持 Kotlinx Serialization 的配置管理器
         * @param key 存储键
         * @param defaultValue 默认值
         * @param json Json 实例，默认使用 Json.Default
         * @return ConfigManager 实例
         */
        inline fun <reified T : Any> createWithSerialization(
            key: String,
            defaultValue: T,
            json: Json = Json.Default
        ): ConfigManager<T> {
            return ConfigManager(
                key = key,
                defaultValue = defaultValue,
                serializer = { value -> json.encodeToString(value) },
                deserializer = { jsonString -> json.decodeFromString(jsonString) }
            )
        }

        /**
         * 创建简单字符串配置管理器
         * @param key 存储键
         * @param defaultValue 默认值
         * @return ConfigManager 实例
         */
        fun createStringConfig(
            key: String,
            defaultValue: String = ""
        ): ConfigManager<String> {
            return ConfigManager(
                key = key,
                defaultValue = defaultValue,
                serializer = { it },
                deserializer = { it }
            )
        }

        /**
         * 创建布尔值配置管理器
         * @param key 存储键
         * @param defaultValue 默认值
         * @return ConfigManager 实例
         */
        fun createBooleanConfig(
            key: String,
            defaultValue: Boolean = false
        ): ConfigManager<Boolean> {
            return ConfigManager(
                key = key,
                defaultValue = defaultValue,
                serializer = { it.toString() },
                deserializer = { it.toBoolean() }
            )
        }

        /**
         * 创建整数配置管理器
         * @param key 存储键
         * @param defaultValue 默认值
         * @return ConfigManager 实例
         */
        fun createIntConfig(
            key: String,
            defaultValue: Int = 0
        ): ConfigManager<Int> {
            return ConfigManager(
                key = key,
                defaultValue = defaultValue,
                serializer = { it.toString() },
                deserializer = { it.toInt() }
            )
        }
    }
}

/**
 * 配置更新异常
 */
class ConfigUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)