package com.holderzone.network.module

import com.holderzone.network.manager.NetworkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ApiService 模块
 * 提供通用的 ApiService 创建支持
 * 
 * 使用方式：
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object YourApiModule {
 *     
 *     @Provides
 *     @Singleton
 *     fun provideUserApiService(
 *         apiServiceFactory: ApiServiceFactory
 *     ): UserApiService = apiServiceFactory.create(UserApiService::class.java)
 * }
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiServiceModule {

    /**
     * 提供 ApiService 工厂
     * 用于创建各种 ApiService 实例
     */
    @Provides
    @Singleton
    fun provideApiServiceFactory(
        networkManager: NetworkManager
    ): ApiServiceFactory = ApiServiceFactory(networkManager)
}

/**
 * ApiService 工厂类
 * 负责创建和管理 ApiService 实例
 */
class ApiServiceFactory(
    private val networkManager: NetworkManager
) {
    
    /**
     * 创建 ApiService 实例（使用当前全局 BaseUrl）
     * @param serviceClass ApiService 接口类
     * @return ApiService 实例
     * @throws IllegalStateException 如果未设置 BaseUrl
     */
    fun <T> create(serviceClass: Class<T>): T {
        return networkManager.getApiService(serviceClass)
    }
    
    /**
     * 创建 ApiService 实例（使用指定 BaseUrl）
     * @param serviceClass ApiService 接口类
     * @param baseUrl 指定的 BaseUrl
     * @return ApiService 实例
     */
    fun <T> create(serviceClass: Class<T>, baseUrl: String): T {
        return networkManager.getApiService(serviceClass, baseUrl)
    }
    
    /**
     * 内联函数版本，支持 reified 类型参数
     */
    inline fun <reified T> create(): T {
        return create(T::class.java)
    }
    
    /**
     * 内联函数版本，支持 reified 类型参数和指定 BaseUrl
     */
    inline fun <reified T> create(baseUrl: String): T {
        return create(T::class.java, baseUrl)
    }
}