package com.holderzone.common.mvi

/**
 * Reducer（状态转换器）接口
 * 
 * Reducer 负责根据 Intent 和当前 State 生成新的 State
 * 这是 MVI 架构中状态更新的核心逻辑
 * 
 * @param S State 类型
 * @param I Intent 类型
 */
interface Reducer<S : State, I : Intent> {
    /**
     * 根据 Intent 和当前 State 生成新的 State
     * 
     * @param currentState 当前状态
     * @param intent 用户意图
     * @return 新的状态
     */
    fun reduce(currentState: S, intent: I): S
}

/**
 * 简单的 Reducer 实现，通过函数式编程方式定义状态转换逻辑
 */
class FunctionReducer<S : State, I : Intent>(
    private val reduceFunction: (S, I) -> S
) : Reducer<S, I> {
    override fun reduce(currentState: S, intent: I): S {
        return reduceFunction(currentState, intent)
    }
}

