package com.holderzone.common.mvi

/**
 * MVI 架构中的 State（状态）接口
 * 
 * State 代表 UI 的当前状态，应该是不可变的数据类
 * 所有状态更新都应该通过创建新的 State 实例来实现
 * 
 * @author MVI Framework
 */
interface State {
    /**
     * 判断状态是否处于加载中
     */
    val isLoading: Boolean get() = false
    
    /**
     * 错误信息，如果有错误则不为空
     */
    val error: String? get() = null
}

/**
 * 空状态，用于初始化
 */
object EmptyState : State

