package com.holderzone.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations

@Composable
fun BlurredImageCompat(
    model: Any?,
    modifier: Modifier = Modifier,
    // 高斯模糊参数
    blurRadius: Float = 25f,
    sampling: Float = 3f,
    // 顶部模糊叠层透明度（仅在 maintainClearBase=true 时生效）
    overlayAlpha: Float = 0.0f,
    // 颜色蒙层（可选），如 Color.Black.copy(alpha = 0.2f)
    scrimColor: Color = Color.Transparent,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    maintainClearBase: Boolean = false,
) {
    Box(modifier = modifier) {
        val context = LocalContext.current

        if (maintainClearBase) {
            // 底部清晰图
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .transformations(
                        BlurTransformation(context, blurRadius, sampling)
                    )
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.matchParentSize()
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .transformations(
                        BlurTransformation(context, blurRadius, sampling)
                    )
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(overlayAlpha)
            )
        } else {
            // 单层：整体模糊
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .transformations(
                        BlurTransformation(context, blurRadius, sampling)
                    )
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.matchParentSize()
            )
        }

        // 颜色蒙层（可选）
        if (scrimColor.alpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(scrimColor)
            )
        }
    }
}