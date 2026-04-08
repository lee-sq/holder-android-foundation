package com.holderzone.network.manager

import com.holderzone.network.dynamic.BaseUrlManager
import com.holderzone.network.dynamic.DynamicRetrofitProvider
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络管理中心
 * 统一管理所有网络相关功能，包括BaseUrl管理和Retrofit实例管理
 *
 * 使用方式：
 * 1. 在Repository中只需注入NetworkManager
 * 2. 通过NetworkManager获取API服务实例
 * 3. 通过NetworkManager管理BaseUrl
 */
@Singleton
class NetworkManager @Inject constructor(
    private val retrofitProvider: DynamicRetrofitProvider,
    private val baseUrlManager: BaseUrlManager
) {

    // ==================== BaseUrl 管理 ====================

    /**
     * 当前BaseUrl的StateFlow，可用于监听BaseUrl变化
     */
    val currentBaseUrl: StateFlow<String?> = baseUrlManager.currentBaseUrl

    /**
     * 设置全局BaseUrl
     * @param baseUrl 新的BaseUrl
     */
    fun setBaseUrl(baseUrl: String) {
        baseUrlManager.setBaseUrl(baseUrl)
    }

    /**
     * 清除当前BaseUrl
     */
    fun clearBaseUrl() {
        baseUrlManager.clearBaseUrl()
    }

    /**
     * 检查是否已设置BaseUrl
     * @return true表示已设置，false表示未设置
     */
    fun hasBaseUrl(): Boolean {
        return baseUrlManager.hasBaseUrl()
    }

    /**
     * 获取当前BaseUrl
     * @return 当前BaseUrl，如果未设置则返回null
     */
    fun getCurrentBaseUrl(): String? {
        return baseUrlManager.getCurrentBaseUrl()
    }

    // ==================== API 服务获取 ====================

    /**
     * 获取API服务实例（使用当前全局BaseUrl）
     * @return API服务实例
     * @throws IllegalStateException 如果未设置BaseUrl
     */
    fun <T> getApiService(serviceClass: Class<T>): T {
        val currentUrl = getCurrentBaseUrl()
            ?: throw IllegalStateException(
                "BaseUrl未设置，请先调用setBaseUrl()方法。" +
                        "提示：如果您在应用启动时遇到此错误，请确保在使用网络服务前先设置BaseUrl，"
            )
        return retrofitProvider.getRetrofit(currentUrl).create(serviceClass)
    }

    /**
     * 安全获取API服务实例（使用当前全局BaseUrl）
     * 如果BaseUrl未设置，返回null而不是抛出异常
     * @return API服务实例，如果BaseUrl未设置则返回null
     */
    fun <T> getApiServiceSafely(serviceClass: Class<T>): T? {
        val currentUrl = getCurrentBaseUrl() ?: return null
        return retrofitProvider.getRetrofit(currentUrl).create(serviceClass)
    }

    /**
     * 获取API服务实例（使用指定BaseUrl）
     * @param baseUrl 指定的BaseUrl
     * @return API服务实例
     */
    fun <T> getApiService(serviceClass: Class<T>, baseUrl: String): T {
        return retrofitProvider.getRetrofit(baseUrl).create(serviceClass)
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除当前BaseUrl的Retrofit缓存
     */
    fun clearCurrentCache() {
        val currentUrl = getCurrentBaseUrl()
        if (currentUrl != null) {
            clearCache(currentUrl)
        }
    }

    /**
     * 清除指定BaseUrl的Retrofit缓存
     * @param baseUrl 要清除缓存的BaseUrl
     */
    fun clearCache(baseUrl: String) {
        retrofitProvider.clearCache(baseUrl)
    }

    /**
     * 清除所有Retrofit缓存
     */
    fun clearAllCache() {
        retrofitProvider.clearAllCache()
    }

    // ==================== 便捷方法 ====================

    /**
     * 切换到新的BaseUrl并清除旧缓存
     * @param newBaseUrl 新的BaseUrl
     * @param clearOldCache 是否清除旧的缓存，默认为true
     */
    fun switchBaseUrl(newBaseUrl: String, clearOldCache: Boolean = true) {
        val oldBaseUrl = getCurrentBaseUrl()
        setBaseUrl(newBaseUrl)

        if (clearOldCache && oldBaseUrl != null && oldBaseUrl != newBaseUrl) {
            clearCache(oldBaseUrl)
        }
    }

    /**
     * 重置网络管理器（清除BaseUrl和所有缓存）
     */
    fun reset() {
        clearBaseUrl()
        clearAllCache()
    }
}