package com.holderzone.common.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavOptions
import com.holderzone.navigation.AppNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


/**
 * 基础ViewModel
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * 在主线程中执行一个协程
     */
    protected fun launchOnUI(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Main) { block() }
    }

    /**
     * 在IO线程中执行一个协程
     */
    protected fun launchOnIO(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * 导航回上一页
     */
    fun navigateBack() {
        viewModelScope.launch {
            AppNavigator.navigateBack()
        }
    }

    /**
     * 导航回上一页并携带结果
     */
    fun navigateBack(result: Map<String, Any>) {
        viewModelScope.launch {
            AppNavigator.navigateBack(result)
        }
    }

    /**
     * 导航返回上一页并同时刷新
     */
    fun navigateBackAndRefresh() {
        navigateBack(mapOf("refresh" to true))
    }

    /**
     * 导航返回到目的栈
     */
    fun navigateBackTo(
        route: String,
        inclusive: Boolean = false,
        result: Map<String, Any> = emptyMap()
    ) {
        viewModelScope.launch {
            AppNavigator.navigateBackTo(route, inclusive, result)
        }
    }

    /**
     * 导航返回到目的栈并刷新
     */
    fun navigateBackAndRefreshTo(route: String, inclusive: Boolean = false) {
        viewModelScope.launch {
            AppNavigator.navigateBackTo(route, inclusive, mapOf("refresh" to true))
        }
    }

    /**
     * 导航到指定路由
     * @param route 目标路由
     */
    fun toPage(route: String) {
        viewModelScope.launch {
            AppNavigator.navigateTo(route)
        }
    }

    /**
     * 携带参数导航到指定路由
     * @param route 基础路由
     * @param args 参数Map
     */
    fun toPage(route: String, args: Map<String, Any>) {
        viewModelScope.launch {
            AppNavigator.navigateTo(buildRouteWithArgs(route, args))
        }
    }

    /**
     * 构建带参数的路由
     *
     * @param baseRoute 基础路由
     * @param args 参数Map
     * @return 完整路由字符串
     */
    private fun buildRouteWithArgs(baseRoute: String, args: Map<String, Any>): String {
        if (args.isEmpty()) return baseRoute

        val argString = args.entries.joinToString("&") { (key, value) ->
            "$key=${value.toString().replace(" ", "%20")}"
        }

        return if (baseRoute.contains("?")) {
            // 路由已经有参数
            "$baseRoute&$argString"
        } else {
            // 路由没有参数
            "$baseRoute?$argString"
        }
    }

    /**
     * 关闭当前页面并导航到指定路由
     *
     * @param route 目标路由
     * @param currentRoute 当前页面路由，将被关闭
     * @param params 目标路由携带的参数
     */
    fun toPageAndCloseCurrent(
        route: String,
        currentRoute: String,
        params: Map<String, Any> = emptyMap()
    ) {
        viewModelScope.launch {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(
                    route = currentRoute,
                    inclusive = true,  // 设为true表示当前页面也会被弹出
                    saveState = false  // 不保存状态
                )
                .build()
            val targetRoute = buildRouteWithArgs(route, params)
            AppNavigator.navigateTo(targetRoute, navOptions)
        }
    }
}
