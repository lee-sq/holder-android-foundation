package com.holderzone.network.module

import android.util.Log
import com.holderzone.logger.logger
import com.holderzone.network.base.DateSerializer
import com.holderzone.network.converter.asConverterFactory
import com.holderzone.network.interceptor.LoggingInterceptor
import com.holderzone.network.retrofit.BaseOkHttpClient
import com.holderzone.network.retrofit.CoroutineCallAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 网络模块
 * 提供网络相关的依赖注入配置
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 提供JSON序列化配置
     */
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            // 忽略未知字段
            ignoreUnknownKeys = true
            // 允许空值
            coerceInputValues = true
            // 使用默认值
            encodeDefaults = true
            // 宽松模式
            isLenient = true
            serializersModule = SerializersModule { contextual(DateSerializer) }
        }
    }

    /**
     * 提供日志拦截器
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): LoggingInterceptor =
        LoggingInterceptor(object : LoggingInterceptor.Logger {
            override fun log(message: String) {
                if (logger.hasInit) {
                    try {
                        logger.t("HttpLog").i(message)
                    } catch (e: Throwable) {
                        Log.i("HttpLog", "解析失败: $message")
                    }
                } else {
                    Log.i("HttpLog", "logger未初始化")
                }
            }
        })

    /**
     * 提供通用拦截器列表
     * 子类可以通过重写此方法来添加自定义拦截器
     */
    @Provides
    @Singleton
    @CommonInterceptors
    fun provideCommonInterceptors(
        loggingInterceptor: LoggingInterceptor
    ): @JvmSuppressWildcards List<Interceptor> {
        val interceptors = listOf(
            loggingInterceptor.apply {
                level = LoggingInterceptor.Level.BODY
            }
        )
        return interceptors
    }

    /**
     * 提供通用OkHttpClient
     * 包含基础配置和通用拦截器
     */
    @Provides
    @Singleton
    @CommonOkHttpClient
    fun provideCommonOkHttpClient(
        @CommonInterceptors interceptors: @JvmSuppressWildcards List<Interceptor>
    ): OkHttpClient = BaseOkHttpClient.create(interceptors)

    /**
     * 提供基础Retrofit Builder
     * 具体的App可以基于此Builder创建自己的Retrofit实例
     */
    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        @CommonOkHttpClient okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit.Builder = Retrofit.Builder()
        .client(okHttpClient)
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .addConverterFactory(json.asConverterFactory(requireNotNull("application/json".toMediaTypeOrNull())))
}

/**
 * 限定符注解
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CommonInterceptors

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CommonOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl