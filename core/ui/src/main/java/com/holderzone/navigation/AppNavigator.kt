package com.holderzone.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 导航管理器。
 *
 * 当前已经从独立的 `:core:navigation` 模块收回到 `:core:ui` 源码层，
 * 这样可以减少低收益的细粒度拆分，同时保留原有包名，避免业务侧 import 震荡。
 */
object AppNavigator {
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    /**
     * 导航到指定路由。
     */
    suspend fun navigateTo(route: String, navOptions: NavOptions? = null) {
        _navigationEvents.emit(NavigationEvent.NavigateTo(route, navOptions))
    }

    /**
     * 返回上一页。
     */
    suspend fun navigateBack() {
        _navigationEvents.emit(NavigationEvent.NavigateBack())
    }

    /**
     * 返回上一页并携带结果。
     */
    suspend fun navigateBack(result: Map<String, Any>) {
        _navigationEvents.emit(NavigationEvent.NavigateBack(result))
    }

    /**
     * 返回到指定路由，并把结果回写给目标页面。
     */
    suspend fun navigateBackTo(
        route: String,
        inclusive: Boolean = false,
        result: Map<String, Any>,
    ) {
        _navigationEvents.emit(NavigationEvent.NavigateBackTo(route, inclusive, result))
    }
}

/**
 * 处理导航事件的 `NavController` 扩展函数。
 *
 * 所有页面层只需要订阅 `AppNavigator.navigationEvents`，
 * 再把事件统一交给这里处理即可。
 */
fun NavController.handleNavigationEvent(event: NavigationEvent) {
    when (event) {
        is NavigationEvent.NavigateTo -> {
            navigate(event.route, event.navOptions)
        }

        is NavigationEvent.NavigateBack -> {
            val target = previousBackStackEntry
            val popped = popBackStack()
            val receiver = if (popped) currentBackStackEntry else target
            event.result?.forEach { (key, value) ->
                receiver?.savedStateHandle?.set(key, value)
            }
        }

        is NavigationEvent.NavigateBackTo -> {
            popBackStack(event.route, event.inclusive)
            event.result?.let { result ->
                currentBackStackEntry?.savedStateHandle?.apply {
                    result.forEach { (key, value) ->
                        set(key, value)
                    }
                }
            }
        }
    }
}
