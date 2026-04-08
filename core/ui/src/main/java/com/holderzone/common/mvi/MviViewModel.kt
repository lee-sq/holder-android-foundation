package com.holderzone.common.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.holderzone.common.vm.BaseNetworkViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构的 ViewModel 基类
 *
 * 负责处理 Intent 并更新 State，实现单向数据流
 *
 * @param S State 类型
 * @param I Intent 类型
 * @param initialState 初始状态
 * @param reducer 状态转换器（可选，如果不提供则需要在子类中重写 processIntent 方法）
 *
 * @author MVI Framework
 */
abstract class MviViewModel<S : State, I : Intent>(
    initialState: S,
    private val reducer: Reducer<S, I>? = null,
    savedStateHandle: SavedStateHandle? = null
) : BaseNetworkViewModel(savedStateHandle) {


    /**
     * 当前状态的可变流
     */
    private val _state = MutableStateFlow(initialState)

    /**
     * 对外暴露的只读状态流
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * 获取当前状态
     */
    val currentState: S
        get() = _state.value

    /**
     * 处理 Intent
     *
     * 如果提供了 reducer，则使用 reducer 来处理
     * 否则调用子类实现的 processIntent 方法
     *
     * @param intent 用户意图
     */
    fun processIntent(intent: I) {
        viewModelScope.launch {
            try {
                if (reducer != null) {
                    // 使用 reducer 处理
                    val newState = reducer.reduce(currentState, intent)
                    _state.update { newState }
                } else {
                    // 调用子类实现
                    handleIntent(intent)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * 处理 Intent 的抽象方法
     * 子类需要实现此方法来处理具体的业务逻辑
     *
     * @param intent 用户意图
     */
    protected open suspend fun handleIntent(intent: I) {
        // 默认实现为空，子类可以重写
    }

    /**
     * 更新状态
     *
     * @param update 状态更新函数
     */
    protected fun updateState(update: (S) -> S) {
        _state.update(update)
    }

    /**
     * 直接设置新状态
     *
     * @param newState 新状态
     */
    protected fun setState(newState: S) {
        _state.value = newState
    }

    /**
     * 处理错误
     *
     * @param error 异常
     */
    protected open fun handleError(error: Throwable) {
        // 默认实现：更新状态为错误状态
        updateState { state ->
            createErrorState(state, error.message ?: "未知错误")
        }
    }

    /**
     * 创建错误状态
     * 子类可以重写此方法来定义错误状态的创建逻辑
     *
     * @param currentState 当前状态
     * @param errorMessage 错误信息
     * @return 包含错误信息的新状态
     */
    protected open fun createErrorState(currentState: S, errorMessage: String): S {
        // 默认实现：如果状态支持错误，则更新错误信息
        // 子类需要重写此方法来实现具体的错误状态创建逻辑
        return currentState
    }

    /**
     * 设置加载状态
     *
     * @param isLoading 是否加载中
     */
    protected fun setLoading(isLoading: Boolean) {
        updateState { state ->
            createLoadingState(state, isLoading)
        }
    }

    /**
     * 创建加载状态
     * 子类可以重写此方法来定义加载状态的创建逻辑
     *
     * @param currentState 当前状态
     * @param isLoading 是否加载中
     * @return 包含加载状态的新状态
     */
    protected open fun createLoadingState(currentState: S, isLoading: Boolean): S {
        // 默认实现：如果状态支持加载，则更新加载状态
        // 子类需要重写此方法来实现具体的加载状态创建逻辑
        return currentState
    }
}


