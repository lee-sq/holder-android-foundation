package com.holderzone.widget.toast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holderzone.common.toast.Duration
import com.holderzone.common.toast.ToastEvent
import com.holderzone.common.toast.ToastType
import com.holderzone.common.toast.Toaster
import com.holderzone.widget.loading.LoadingWidget
import com.holderzone.widget.OverLay
import com.holderzone.widget.loading.ThreeTadpole
import com.holderzone.widget.theme.Purple80
import com.holderzone.widget.theme.blue3D5BFE
import com.holderzone.widget.theme.whiteFFFFFFFF

@Composable
fun CommonToastHost() {
    val snackBarHostState = remember { SnackbarHostState() }

    val toastMap = mapOf(
        ToastType.SUCCESS to Success(),
        ToastType.ERROR to Error(),
        ToastType.INFO to Info()
    )

    var currentToastProperty by remember { mutableStateOf<IToastProperty>(Info()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(snackBarHostState) {
        Toaster.events.collect { event ->
            when (event) {
                is ToastEvent.ShowToast -> {
                    isLoading = false
                    currentToastProperty = toastMap[event.type] ?: Info()
                    val duration = when (event.duration) {
                        Duration.SHORT -> SnackbarDuration.Short
                        Duration.LONG -> SnackbarDuration.Long
                    }
                    snackBarHostState.showSnackbar(
                        message = event.message,
                        duration = duration
                    )
                }

                is ToastEvent.ShowLoading -> {
                    // 打开加载层（不依赖 snackbar）
                    isLoading = true
                }

                is ToastEvent.DismissLoading -> {
                    // 关闭加载层并尝试收起当前 snackbar
                    isLoading = false
                    snackBarHostState.currentSnackbarData?.dismiss()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 117.dp)
        ) { data ->
            // 只负责 toast 样式
            Toast(data.visuals.message, currentToastProperty)
        }

        // 独立的全屏 Loading 覆盖层
        if (isLoading) {
            LoadingContainer()
        }
    }
}

@Composable
private fun LoadingContainer() {
    OverLay {
        ThreeTadpole(headColor = whiteFFFFFFFF)
    }
}

@Composable
private fun Toast(
    toastMessage: String,
    toastProperty: IToastProperty,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 47.dp)
            .wrapContentWidth()
            .background(
                color = toastProperty.getBackgroundColor(),
                shape = RoundedCornerShape(23.dp)
            )
            .border(
                width = 1.dp,
                color = toastProperty.getBorderColor(),
                shape = RoundedCornerShape(23.dp)
            )
            .padding(
                start = 27.dp,
                end = 27.dp
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (toastProperty.getResourceId() != null) {
            Image(
                contentDescription = toastMessage,
                painter = painterResource(id = toastProperty.getResourceId()!!),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(13.dp))
        }
        Text(
            text = toastMessage,
            style = TextStyle(
                color = toastProperty.getTextColor(),
                fontSize = 21.sp
            )
        )
    }
}