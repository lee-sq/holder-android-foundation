package com.holderzone.widget.drop_down_selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holderzone.widget.theme.black222222
import com.holderzone.widget.theme.blue144EB3
import com.holderzone.widget.theme.blue3B82F6
import com.holderzone.widget.theme.whiteFFFFFFFF
import com.holderzone.widget.modifier.setPressColorAndBackground

@Composable
fun <T> SingleChoiceDropMenu(
    data: List<DropDownSelectionVO<T>> = emptyList(),
    isExpanded: Boolean,
    modifier: Modifier,
    onDismissRequest: () -> Unit,
    onClick: (data: DropDownSelectionVO<T>) -> Unit
) {
    CustomDropdownMenu(
        isExpanded,
        modifier,
        onDismissRequest
    ) {
        data.forEach { item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .setPressColorAndBackground(
                        pressColor = blue3B82F6.copy(alpha = 0.05f),
                        backgroundColor = whiteFFFFFFFF
                    ) {
                        onClick(item)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.label,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = if (item.isSelect) blue144EB3 else black222222,
                        fontWeight = if (item.isSelect) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    }
}