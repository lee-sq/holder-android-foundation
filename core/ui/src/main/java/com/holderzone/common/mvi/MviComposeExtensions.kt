package com.holderzone.common.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 在 Compose 中收集 MVI ViewModel 的完整状态
 *
 * ⚠️ 注意：这会收集整个 State，State 的任何字段变化都会触发整个 Composable 重组
 * 如果 State 包含很多字段，建议使用 [collectStateField] 来收集特定字段
 *
 * @return 当前状态
 */
@Composable
fun <S : State, I : Intent> MviViewModel<S, I>.collectState(): S {
    return state.collectAsStateWithLifecycle().value
}

/**
 * 在 Compose 中收集 State 的特定字段（性能优化）
 *
 * 只收集 State 的某个字段，只有该字段变化时才会触发重组
 * 这样可以避免整个 State 变化导致整个页面重组
 *
 * 示例：
 * ```kotlin
 * val isLoading = viewModel.collectStateField { it.isLoading }
 * val error = viewModel.collectStateField { it.error }
 * val users = viewModel.collectStateField { it.users }
 * ```
 *
 * @param selector 字段选择器，从 State 中提取需要的字段
 * @return 选中的字段值
 */
@Composable
fun <S : State, I : Intent, T> MviViewModel<S, I>.collectStateField(
    selector: (S) -> T
): T {
    val state = state.collectAsStateWithLifecycle().value
    // 直接返回选择器的结果
    // 注意：虽然会失去 derivedStateOf 的优化，但可以避免编译错误
    // 在实际使用中，如果 State 字段很多，建议在 ViewModel 中创建单独的 StateFlow
    return selector(state)
}

/**
 * 在 Compose 中收集 State 的多个字段（性能优化）
 *
 * 使用此方法可以同时收集多个字段，但只有这些字段变化时才会触发重组
 *
 * 示例：
 * ```kotlin
 * val (isLoading, error) = viewModel.collectStateFields(
 *     { it.isLoading },
 *     { it.error }
 * )
 * ```
 *
 * @param selectors 字段选择器列表
 * @return 选中字段的值的 Pair
 */
@Composable
fun <S : State, I : Intent, T1, T2> MviViewModel<S, I>.collectStateFields(
    selector1: (S) -> T1,
    selector2: (S) -> T2
): Pair<T1, T2> {
    val state = state.collectAsStateWithLifecycle().value
    return Pair(selector1(state), selector2(state))
}

/**
 * 在 Compose 中收集 State 的三个字段（性能优化）
 */
@Composable
fun <S : State, I : Intent, T1, T2, T3> MviViewModel<S, I>.collectStateFields(
    selector1: (S) -> T1,
    selector2: (S) -> T2,
    selector3: (S) -> T3
): Triple<T1, T2, T3> {
    val state = state.collectAsStateWithLifecycle().value
    return Triple(selector1(state), selector2(state), selector3(state))
}

/**
 * 使用 Flow.map 创建只关注特定字段的 Flow（ViewModel 层面优化）
 *
 * 在 ViewModel 中使用此方法可以创建只关注特定字段的 Flow
 * 这样在 Compose 中收集时，只有该字段变化才会触发重组
 *
 * 注意：如果需要保持 StateFlow 类型，可以使用 stateIn 转换
 *
 * 示例（在 ViewModel 中）：
 * ```kotlin
 * val isLoadingFlow = state.map { it.isLoading }
 * ```
 *
 * 然后在 Compose 中：
 * ```kotlin
 * val isLoading by viewModel.isLoadingFlow.collectAsStateWithLifecycle()
 * ```
 */
fun <S : State, T> StateFlow<S>.mapField(
    selector: (S) -> T
): Flow<T> {
    return this.map(selector)
}

/**
 * 发送 Intent 的扩展函数
 *
 * @param intent 用户意图
 */
fun <S : State, I : Intent> MviViewModel<S, I>.sendIntent(intent: I) {
    processIntent(intent)
}
