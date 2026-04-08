package com.holderzone.widget.dialog

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holderzone.widget.OverLay
import com.holderzone.widget.spacer.HorizontalSpacer
import com.holderzone.widget.theme.blue3D5BFE
import com.holderzone.widget.theme.grayF6F6F6
import com.holderzone.widget.theme.whiteFFFFFFFF

@Composable
fun CommonConfirmDialog(
    content: AnnotatedString = buildAnnotatedString { "" },
    cancelText: String = "取消",
    confirmText: String = "确认",
    cancel: () -> Unit,
    confirm: () -> Unit
) {
    val isLand = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    OverLay {

        val width = this.maxWidth * (if (isLand) 0.5f else 0.9f)
        val height = this.maxHeight * (if (isLand) 0.55f else 0.32f)
        Column(
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(whiteFFFFFFFF)
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(120.dp, Alignment.CenterVertically)
        ) {
            Text(content)
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    cancelText,
                    style = TextStyle(fontSize = 26.sp, color = blue3D5BFE),
                    modifier = Modifier
                        .clickable {
                            cancel()
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(grayF6F6F6, shape = RoundedCornerShape(6.dp))
                        .padding(vertical = 16.dp, horizontal = 64.dp)
                )
                HorizontalSpacer(44.dp)
                Text(
                    confirmText,
                    style = TextStyle(fontSize = 26.sp, color = whiteFFFFFFFF),
                    modifier = Modifier
                        .clickable {
                            confirm()
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(blue3D5BFE)
                        .padding(vertical = 16.dp, horizontal = 64.dp)
                )
            }
        }
    }
}