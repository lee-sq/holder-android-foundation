package com.holderzone.network.dynamic

import com.holderzone.network.module.CommonOkHttpClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态Retrofit提供器
 * 支持运行时动态更改baseUrl
 */
@Singleton
open class DynamicRetrofitProvider @Inject constructor(
    @CommonOkHttpClient private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    
    // 缓存不同baseUrl的Retrofit实例
    private val retrofitCache = mutableMapOf<String, Retrofit>()
    
    /**
     * 根据baseUrl获取Retrofit实例
     * 如果已存在则返回缓存，否则创建新的
     */
    fun getRetrofit(baseUrl: String): Retrofit {
        return retrofitCache.getOrPut(baseUrl) {
            createRetrofit(baseUrl)
        }
    }
    
    /**
     * 创建新的Retrofit实例
     */
    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    /**
     * 清除指定baseUrl的缓存
     */
    fun clearCache(baseUrl: String) {
        retrofitCache.remove(baseUrl)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        retrofitCache.clear()
    }
    
    /**
     * 获取API服务实例
     */
    inline fun <reified T> getApiService(baseUrl: String): T {
        return getRetrofit(baseUrl).create(T::class.java)
    }
}