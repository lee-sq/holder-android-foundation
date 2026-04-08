package com.holderzone.utils.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 网络状态枚举
 */
enum class NetworkStatus {
    /** 网络正常 */
    NORMAL,

    /** 弱网环境 */
    WEAK,

    /** 完全没有网络 */
    NONE
}

/**
 * 网络监听器
 * 用于监听网络状态变化，包括无网络和弱网环境
 */
class NetworkMonitor(
    private val context: Context,
    /**
     * Ping 测试的服务器地址列表，按优先级排序
     * 默认使用国内常用的稳定服务器
     */
    private val pingHosts: List<String> = listOf(
        "www.baidu.com",
        "https://www.sogou.com/",
        "https://www.tencent.com/zh-cn/"
    ),
    /**
     * Ping 检测间隔（毫秒），默认 15 秒
     * 优化：增加间隔时间，减少频繁检测导致的 GC 压力
     */
    private val pingInterval: Long = 15000,
    /**
     * 网络请求超时时间（毫秒），默认 3 秒
     */
    private val pingTimeout: Int = 3000
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(NetworkStatus.NONE)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 协程作用域，用于执行 ping 检测
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private var isMonitoring = false

    /**
     * 开始监听网络状态
     * 注意：需要在 AndroidManifest.xml 中声明 ACCESS_NETWORK_STATE 权限
     */
    fun startMonitoring() {
        if (networkCallback != null) {
            return // 已经启动监听
        }

        // 先检查当前网络状态
        checkCurrentNetworkStatus()

        // 注册默认网络回调，这样可以监听所有网络状态变化，包括网络断开
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 网络可用时检查网络质量
                checkNetworkQuality(network)
            }

            override fun onLost(network: Network) {
                // 网络丢失时，检查是否还有其他可用网络
                checkCurrentNetworkStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // 网络能力变化时，触发 ping 检测（ping 检测会更新网络状态）
                // 不直接调用 checkNetworkQuality，让 ping 检测来判定网络质量
                scope.launch {
                    // 延迟一小段时间，确保网络连接稳定后再检测
                    delay(500)
                    if (isMonitoring) {
                        val pingResult = performPingTest()
                        _networkStatus.value = pingResult
                    }
                }
            }

            override fun onUnavailable() {
                // 当没有满足条件的网络时触发（Android 6.0+）
                _networkStatus.value = NetworkStatus.NONE
            }
        }

        // 使用 registerDefaultNetworkCallback 监听默认网络变化
        // 这比 registerNetworkCallback 更适合监听网络断开的情况
        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)

        // 启动定期 ping 检测
        startPingMonitoring()
    }

    /**
     * 启动定期 ping 检测
     * 优化：
     * 1. 完全没有网络时不执行 ping，直接设置为 NONE，避免浪费资源
     * 2. 增加检测间隔，减少 GC 压力
     */
    private fun startPingMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        pingJob = scope.launch {
            while (isMonitoring) {
                try {
                    // 先检查基本网络连接
                    val activeNetwork = connectivityManager.activeNetwork
                    if (activeNetwork == null) {
                        // 完全没有网络，直接设置为 NONE，不执行 ping
                        _networkStatus.value = NetworkStatus.NONE
                        // 等待网络回调通知（onAvailable），不主动检测
                        delay(60000) // 60秒后再次检查，避免无限等待
                        continue
                    }

                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    if (capabilities == null ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) {
                        // 没有有效的网络连接，直接设置为 NONE，不执行 ping
                        _networkStatus.value = NetworkStatus.NONE
                        // 等待网络回调通知，不主动检测
                        delay(60000) // 60秒后再次检查
                        continue
                    }

                    // 有网络连接时，才执行 ping 检测（用于检测弱网）
                    val pingResult = performPingTest()
                    _networkStatus.value = pingResult
                    
                    // 根据 ping 结果决定下次检测间隔
                    val nextDelay = when (pingResult) {
                        NetworkStatus.NORMAL -> pingInterval // 正常：15秒
                        NetworkStatus.WEAK -> pingInterval // 弱网：15秒（统一间隔，减少 GC）
                        NetworkStatus.NONE -> 60000 // 没有网络：60秒（等待网络恢复）
                    }
                    delay(nextDelay)

                } catch (e: Exception) {
                    // 如果 ping 检测失败，可能是网络问题
                    _networkStatus.value = NetworkStatus.NONE
                    // 出错时使用长延迟，等待网络恢复
                    delay(60000)
                }
            }
        }
    }

    /**
     * 执行 ping 测试，返回网络状态
     * 通过 HTTP 请求测试网络延迟，类似 ping 的效果
     * 优化：优先测试第一个服务器，如果成功且延迟正常就直接返回，减少对象创建
     */
    private suspend fun performPingTest(): NetworkStatus {
        var successCount = 0
        var totalLatency = 0L
        val testCount = pingHosts.size
        var firstSuccessLatency = -1L

        // 优化：优先测试第一个服务器
        for ((index, host) in pingHosts.withIndex()) {
            try {
                val latency = pingHost(host)
                if (latency > 0) {
                    successCount++
                    totalLatency += latency
                    
                    // 记录第一个成功的延迟
                    if (firstSuccessLatency < 0) {
                        firstSuccessLatency = latency
                    }
                    
                    // 优化：如果第一个服务器 ping 成功且延迟正常（< 1000ms），直接返回正常
                    // 不需要测试其他服务器，减少对象创建和网络请求
                    if (index == 0 && latency < 1000) {
                        return NetworkStatus.NORMAL
                    }
                    
                    // 如果第一个服务器延迟较高，继续测试其他服务器
                    // 但如果已经测试了 2 个服务器且都成功，也直接返回结果
                    if (successCount >= 2) {
                        break
                    }
                }
            } catch (e: Exception) {
                // 某个服务器 ping 失败，继续测试下一个
                continue
            }
        }

        // 如果所有服务器都 ping 失败，判定为完全没有网络
        if (successCount == 0) {
            return NetworkStatus.NONE
        }

        // 计算平均延迟
        val avgLatency = totalLatency / successCount

        // 根据延迟判断网络质量
        return when {
            // 延迟 > 2000ms 或成功率 < 50%，判定为弱网
            avgLatency > 2000 || successCount < testCount / 2 -> NetworkStatus.WEAK
            // 延迟 > 1000ms，判定为弱网
            avgLatency > 1000 -> NetworkStatus.WEAK
            // 延迟正常，判定为正常
            else -> NetworkStatus.NORMAL
        }
    }

    /**
     * Ping 单个主机，返回延迟（毫秒）
     * 使用 HTTP HEAD 请求来测试网络连接和延迟
     * 优化：减少对象创建，避免频繁 GC
     */
    private suspend fun pingHost(host: String): Long {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var connection: HttpURLConnection? = null

            try {
                // 优化：使用字符串常量，减少对象创建
                val urlString = if (host.startsWith("http://") || host.startsWith("https://")) {
                    host
                } else {
                    "http://$host"
                }
                
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                
                // 设置连接属性
                connection.requestMethod = "HEAD"
                connection.connectTimeout = pingTimeout
                connection.readTimeout = pingTimeout
                connection.instanceFollowRedirects = false
                // 禁用自动重定向，减少不必要的请求
                connection.useCaches = false

                // 执行请求
                val responseCode = connection.responseCode
                val latency = System.currentTimeMillis() - startTime

                // 如果响应码是 2xx、3xx 或超时但能连接，都认为网络可用
                if (responseCode in 200..399 || latency < pingTimeout) {
                    latency
                } else {
                    -1 // 请求失败
                }
            } catch (e: Exception) {
                // 连接失败或超时，返回 -1 表示失败
                -1
            } finally {
                try {
                    connection?.disconnect()
                } catch (e: Exception) {
                    // 忽略断开连接时的异常
                }
            }
        }
    }

    /**
     * 停止监听网络状态
     */
    fun stopMonitoring() {
        isMonitoring = false
        pingJob?.cancel()
        pingJob = null

        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }

    /**
     * 检查当前网络状态
     */
    private fun checkCurrentNetworkStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            _networkStatus.value = NetworkStatus.NONE
            return
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            _networkStatus.value = NetworkStatus.NONE
            return
        }

        checkNetworkQuality(activeNetwork)
    }

    /**
     * 检查网络质量（基本检查，实际质量由 ping 检测判定）
     */
    private fun checkNetworkQuality(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: run {
                _networkStatus.value = NetworkStatus.NONE
                return
            }

        // 检查是否有网络连接
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        if (!hasInternet || !isValidated) {
            _networkStatus.value = NetworkStatus.NONE
            return
        }

        // 如果有网络连接，触发一次 ping 检测来判定实际网络质量
        // ping 检测会异步更新网络状态
        scope.launch {
            if (isMonitoring) {
                val pingResult = performPingTest()
                _networkStatus.value = pingResult
            }
        }
    }

    /**
     * 获取当前网络状态（同步方法）
     */
    fun getCurrentNetworkStatus(): NetworkStatus {
        return _networkStatus.value
    }

    /**
     * 检查是否有网络连接
     */
    fun isNetworkAvailable(): Boolean {
        return _networkStatus.value != NetworkStatus.NONE
    }

    /**
     * 检查是否为弱网环境
     */
    fun isWeakNetwork(): Boolean {
        return _networkStatus.value == NetworkStatus.WEAK
    }
}

