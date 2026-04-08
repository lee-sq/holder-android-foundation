package com.holderzone.network.retrofit

import android.annotation.SuppressLint
import com.holderzone.network.interceptor.LoggingInterceptor
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object BaseOkHttpClient {

    fun create(interceptors: List<Interceptor>? = null): OkHttpClient {

        val builder = OkHttpClient.Builder()

        builder.connectTimeout(Settings.DEFAULT_CONNECT_TIME, TimeUnit.SECONDS)
            .writeTimeout(Settings.DEFAULT_WRITE_TIME, TimeUnit.SECONDS)
            .readTimeout(Settings.DEFAULT_READ_TIME, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))

        interceptors?.let { interceptorList ->
            interceptorList.forEachIndexed { index, interceptor ->
                builder.addInterceptor(interceptor)
            }
        }
        // 信任所有Https证书。因为服务端是CA证书，肯定安全，所以直接信任就行了。
        builder.sslSocketFactory(sslSocketFactory(), trustManager())
        builder.hostnameVerifier(hostnameVerifier())

        return builder.build()
    }


    private fun sslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(trustManager())
        return SSLContext.getInstance("SSL").run {
            init(null, trustAllCerts, SecureRandom())
            socketFactory
        }
    }

    private fun trustManager() = object : X509TrustManager {

        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkClientTrusted(
            x509Certificates: Array<X509Certificate>,
            s: String
        ) = Unit

        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkServerTrusted(
            x509Certificates: Array<X509Certificate>,
            s: String
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun hostnameVerifier() = HostnameVerifier { _, _ -> true }
}