package com.holderzone.common.toast


sealed class ToastEvent {

    data class ShowToast(val message: String, val type: ToastType, val duration: Duration) :
        ToastEvent()

    data class ShowLoading(var tips: String? = null) : ToastEvent()

    object DismissLoading : ToastEvent()
}


enum class ToastType {
    SUCCESS,
    ERROR,
    INFO
}