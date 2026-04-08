package com.holderzone.network.dynamic

import com.holderzone.network.service.BaseRetrofitService

/**
 * 动态API服务基类
 * 支持动态baseUrl切换的API服务
 */
abstract class DynamicApiService(
    protected val retrofitProvider: DynamicRetrofitProvider,
    protected val baseUrlManager: BaseUrlManager
) : BaseRetrofitService() {
    
    /**
     * 获取API服务实例（使用当前baseUrl）
     * 如果没有设置baseUrl则抛出异常
     */
    protected inline fun <reified T> getApiService(): T {
        val currentBaseUrl = baseUrlManager.getCurrentBaseUrl()
            ?: throw IllegalStateException("未设置BaseUrl，请先调用 setBaseUrl() 方法")
        return retrofitProvider.getApiService<T>(currentBaseUrl)
    }
    
    /**
     * 获取API服务实例（使用指定baseUrl）
     */
    protected inline fun <reified T> getApiService(baseUrl: String): T {
        return retrofitProvider.getApiService<T>(baseUrl)
    }
    
    /**
     * 清除当前baseUrl的缓存
     */
    protected fun clearCurrentCache() {
        val currentBaseUrl = baseUrlManager.getCurrentBaseUrl()
        if (currentBaseUrl != null) {
            retrofitProvider.clearCache(currentBaseUrl)
        }
    }
    
    /**
     * 清除指定baseUrl的缓存
     */
    protected fun clearCache(baseUrl: String) {
        retrofitProvider.clearCache(baseUrl)
    }
    
    /**
     * 清除所有缓存
     */
    protected fun clearAllCache() {
        retrofitProvider.clearAllCache()
    }
}