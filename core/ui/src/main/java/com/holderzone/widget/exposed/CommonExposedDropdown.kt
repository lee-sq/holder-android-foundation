package com.holderzone.widget.exposed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.holderzone.widget.modifier.shadow
import com.holderzone.widget.theme.black
import com.holderzone.widget.theme.whiteFFFFFFFF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonExposedDropdown(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    anchor: @Composable ExposedDropdownMenuBoxScope.() -> Unit, // 外部提供 TextField/锚点（需 .menuAnchor()）
    matchAnchorWidth: Boolean = true,
    maxMenuHeight: Dp = 300.dp,
    focusable: Boolean = false, // 避免抢焦点唤出系统栏（可按需开启）
    content: @Composable () -> Unit // 菜单项插槽
) {

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        anchor() // 外部文本框/锚点（必须 .menuAnchor()）
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier
                .exposedDropdownSize(matchAnchorWidth = matchAnchorWidth)
                .fillMaxWidth()
                .background(whiteFFFFFFFF)
                .heightIn(max = maxMenuHeight)
                .padding(0.dp),
            properties = PopupProperties(focusable)
        ) {
            content()
        }
    }
}
