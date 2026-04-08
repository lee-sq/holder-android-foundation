package com.holderzone.common.mvi

/**
 * MVI 架构中的 Intent（意图）接口
 *
 * Intent 代表用户的操作意图或系统事件
 * 所有用户交互都应该通过 Intent 来表达
 *
 * @author MVI Framework
 */
interface Intent

/**
 * 空意图，用于初始化或占位
 */
object EmptyIntent : Intent

/**
 * 加载意图，用于触发数据加载
 */
sealed class LoadIntent : Intent {
    /**
     * 初始加载
     */
    object Initial : LoadIntent()

    /**
     * 刷新加载
     */
    object Refresh : LoadIntent()

    /**
     * 加载更多
     */
    object LoadMore : LoadIntent()
}

