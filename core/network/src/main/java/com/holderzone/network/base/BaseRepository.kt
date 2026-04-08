package com.holderzone.network.base

import com.holderzone.network.manager.NetworkManager
import com.holderzone.network.service.BaseRetrofitService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository基类
 * 提供统一的网络管理功能，所有Repository只需继承此类并注入NetworkManager
 *
 * 使用方式：
 * ```kotlin
 * @Singleton
 * class UserRepository @Inject constructor(
 *     networkManager: NetworkManager
 * ) : BaseRepository(networkManager) {
 * }
 * ```
 */
abstract class BaseRepository(
    protected val networkManager: NetworkManager
) : BaseRetrofitService() {

    // ==================== BaseUrl 管理 ====================

    /**
     * 当前BaseUrl的StateFlow，可用于监听BaseUrl变化
     */
    protected val currentBaseUrl: StateFlow<String?> = networkManager.currentBaseUrl

    /**
     * 设置全局BaseUrl
     * @param baseUrl 新的BaseUrl
     */
    protected fun setBaseUrl(baseUrl: String) {
        networkManager.setBaseUrl(baseUrl)
    }

    /**
     * 清除当前BaseUrl
     */
    protected fun clearBaseUrl() {
        networkManager.clearBaseUrl()
    }

    /**
     * 检查是否已设置BaseUrl
     * @return true表示已设置，false表示未设置
     */
    fun hasBaseUrl(): Boolean {
        return networkManager.hasBaseUrl()
    }

    /**
     * 获取当前BaseUrl
     * @return 当前BaseUrl，如果未设置则返回null
     */
    protected fun getCurrentBaseUrl(): String? {
        return networkManager.getCurrentBaseUrl()
    }

    // ==================== API 服务获取 ====================

    /**
     * 获取API服务实例（使用当前全局BaseUrl）
     * @param serviceClass API服务接口类
     * @return API服务实例
     * @throws IllegalStateException 如果未设置BaseUrl
     */
    protected fun <T> getApiService(serviceClass: Class<T>): T {
        return networkManager.getApiService(serviceClass)
    }

    /**
     * 获取API服务实例（使用指定BaseUrl）
     * @param serviceClass API服务接口类
     * @param baseUrl 指定的BaseUrl
     * @return API服务实例
     */
    protected fun <T> getApiService(serviceClass: Class<T>, baseUrl: String): T {
        return networkManager.getApiService(serviceClass, baseUrl)
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除当前BaseUrl的Retrofit缓存
     */
    protected fun clearCurrentCache() {
        networkManager.clearCurrentCache()
    }

    /**
     * 清除指定BaseUrl的Retrofit缓存
     * @param baseUrl 要清除缓存的BaseUrl
     */
    protected fun clearCache(baseUrl: String) {
        networkManager.clearCache(baseUrl)
    }

    /**
     * 清除所有Retrofit缓存
     */
    protected fun clearAllCache() {
        networkManager.clearAllCache()
    }

    // ==================== 便捷方法 ====================

    /**
     * 切换到新的BaseUrl并清除旧缓存
     * @param newBaseUrl 新的BaseUrl
     * @param clearOldCache 是否清除旧的缓存，默认为true
     */
    protected fun switchBaseUrl(newBaseUrl: String, clearOldCache: Boolean = true) {
        networkManager.switchBaseUrl(newBaseUrl, clearOldCache)
    }
}