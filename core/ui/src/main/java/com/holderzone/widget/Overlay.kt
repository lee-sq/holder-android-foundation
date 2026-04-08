package com.holderzone.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.holderzone.widget.modifier.debounceNoIndicationClick

@Composable
fun OverLay(
    backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
    cancel: () -> Unit = {},
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .debounceNoIndicationClick {
                cancel()
            }
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // 在 BoxWithConstraints 作用域内调用，content 可以访问 maxWidth/maxHeight
        content()
    }
}