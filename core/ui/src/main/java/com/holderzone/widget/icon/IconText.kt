package com.holderzone.widget.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun RowIconText(
    text: String,
    textStyle: TextStyle,
    paddingValues: Dp,
    iconWidth: Dp = 20.dp,
    iconHeight: Dp = 20.dp,
    leading: Int? = null,
    trailing: Int? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(paddingValues),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Image(
                painterResource(leading),
                contentDescription = text,
                modifier = Modifier.size(iconWidth, iconHeight)
            )
        }
        Text(text = text, style = textStyle)
        if (trailing != null) {
            Image(
                painterResource(trailing),
                contentDescription = text,
                modifier = Modifier.size(iconWidth, iconHeight)
            )
        }
    }
}