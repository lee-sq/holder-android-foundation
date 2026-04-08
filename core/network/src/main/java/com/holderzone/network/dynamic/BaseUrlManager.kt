package com.holderzone.network.dynamic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BaseUrl管理器
 * 支持用户自定义BaseUrl动态切换
 */
@Singleton
class BaseUrlManager @Inject constructor() {
    
    private val _currentBaseUrl = MutableStateFlow<String?>(null)
    val currentBaseUrl: StateFlow<String?> = _currentBaseUrl.asStateFlow()
    
    /**
     * 获取当前BaseUrl
     * 如果没有设置则返回null
     */
    fun getCurrentBaseUrl(): String? {
        return _currentBaseUrl.value
    }
    
    /**
     * 设置BaseUrl
     */
    fun setBaseUrl(baseUrl: String?) {
        if (baseUrl != null && !isValidUrl(baseUrl)) {
            throw IllegalArgumentException("无效的BaseUrl: $baseUrl")
        }
        _currentBaseUrl.value = baseUrl
    }
    
    /**
     * 清除BaseUrl
     */
    fun clearBaseUrl() {
        _currentBaseUrl.value = null
    }
    
    /**
     * 检查是否已设置BaseUrl
     */
    fun hasBaseUrl(): Boolean {
        return _currentBaseUrl.value != null
    }
    
    /**
     * 验证URL格式
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
}