package com.holderzone.common.vm

import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import com.holderzone.navigation.AppNavigator
import com.holderzone.common.toast.Duration
import com.holderzone.common.toast.Toaster
import com.holderzone.core.ui.R
import com.holderzone.utils.storage.StringResHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * 基础ViewModel
 */
abstract class BaseNetworkViewModel(
    protected val savedStateHandle: SavedStateHandle? = null,
) : BaseViewModel() {

    /**
     * 视图层调用此方法，监听页面刷新信号。
     * @param backStackEntry 当前页面的NavBackStackEntry
     * @param key 刷新信号的key，默认是"refresh"，可自定义
     *
     * 用法：在Composable中调用 viewModel.observeRefreshState(backStackEntry, key = "refreshXXX")
     * 只需调用一次，自动去重和解绑，无内存泄漏。
     */
    fun observeRefreshState(backStackEntry: NavBackStackEntry?, key: String = "refresh") {
        if (backStackEntry == null) return
        val owner: LifecycleOwner = backStackEntry
        backStackEntry.savedStateHandle
            .getLiveData<Boolean>(key)
            .observe(owner, Observer<Boolean> { value ->
                if (value) {
                    executeRequest()
                    // 只刷新一次
                    backStackEntry.savedStateHandle[key] = false
                }
            })
    }

    fun <T> observeState(
        backStackEntry: NavBackStackEntry?,
        key: String,
        callBack: (t: T) -> Unit
    ) {
        if (backStackEntry == null) return
        val owner: LifecycleOwner = backStackEntry
        backStackEntry.savedStateHandle
            .getLiveData<T>(key)
            .observe(owner, Observer<T?> { value ->
                if (value != null) {
                    callBack(value)
                    backStackEntry.savedStateHandle[key] = null
                }
            })
    }

    /**
     * 执行初始化网络请求
     */
    open fun executeRequest() {

    }

    /**
     * 是否网络请求的时候是否开启主loading UI
     */
    open fun enableCustomLoadingUI(): Boolean = true

    fun <T> Flow<T>.loading(): Flow<T> {
        return this.onStart {
            Toaster.showLoading()
        }
            .onCompletion {
                Toaster.dismissLoading()
            }
    }

    fun showSuccessToast(
        message: String = StringResHelper.getString(R.string.toast_success_default_text),
        duration: Duration = Duration.SHORT
    ) {
        viewModelScope.launch {
            Toaster.showSuccess(message, duration)
        }
    }

    fun showErrorToast(
        message: String = StringResHelper.getString(R.string.toast_error_default_text),
        duration: Duration = Duration.SHORT
    ) {
        viewModelScope.launch {
            Toaster.showError(message, duration)
        }
    }

    fun showInfoToast(
        message: String = StringResHelper.getString(R.string.toast_error_info_text),
        duration: Duration = Duration.SHORT
    ) {
        viewModelScope.launch {
            Toaster.showInfo(message, duration)
        }
    }


    fun getStringRes(@StringRes resId: Int): String = StringResHelper.getString(resId)

    fun getStringRes(@StringRes resId: Int, vararg formatArgs: Any): String =
        StringResHelper.getString(resId, *formatArgs)

}
