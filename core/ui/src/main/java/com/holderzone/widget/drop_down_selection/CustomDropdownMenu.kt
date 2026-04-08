package com.holderzone.widget.drop_down_selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.holderzone.widget.theme.black
import com.holderzone.widget.theme.whiteFFFFFFFF
import com.holderzone.widget.modifier.shadow

@Composable
fun CustomDropdownMenu(
    isExpanded: Boolean,
    modifier: Modifier,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    DropdownMenu(
        isExpanded, onDismissRequest, modifier
            .padding(0.dp)
            .shadow(
                color = black.copy(alpha = 0.21f),
                offsetY = 1.dp,
                offsetX = 0.dp,
                blurRadius = 3.dp
            )
            .background(whiteFFFFFFFF),
        properties = PopupProperties(focusable = false)
    ) {
        content()
    }
}