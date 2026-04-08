package com.holderzone.widget.loading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import com.holderzone.core.ui.R

@Composable
fun LoadingWidget() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    //索引插值动画
    val indexAnimate by infiniteTransition.animateFloat(
        initialValue = 0.0f, targetValue = 360.0f, label = "", animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    Image(
        painter = painterResource(R.drawable.icon_loading),
        contentDescription = "Loading",
        modifier = Modifier.rotate(indexAnimate)
    )
}
