package com.holderzone.navigation

import androidx.navigation.NavOptions

/**
 * 导航事件定义。
 *
 * 这里统一描述页面层会发出的导航意图，
 * 让 ViewModel 不需要直接持有 `NavController`。
 */
sealed class NavigationEvent {
    /**
     * 导航到目标路由。
     */
    data class NavigateTo(
        val route: String,
        val navOptions: NavOptions? = null,
    ) : NavigationEvent()

    /**
     * 返回上一页。
     *
     * `result` 用于把数据回传给上一页。
     */
    data class NavigateBack(
        val result: Map<String, Any>? = null,
    ) : NavigationEvent()

    /**
     * 返回到指定路由。
     */
    data class NavigateBackTo(
        val route: String,
        val inclusive: Boolean = false,
        val result: Map<String, Any>? = null,
    ) : NavigationEvent()
}
