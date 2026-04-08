@file:Suppress("DEPRECATION", "unused")

package com.holderzone.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.annotation.RequiresApi
import androidx.core.graphics.applyCanvas
import coil3.size.Size
import coil3.transform.Transformation

/**
 * A [coil3.transform.Transformation] that applies a Gaussian blur to an image.
 *
 * @param context The [Context] used to create a [RenderScript] instance.
 * @param radius The radius of the blur.
 * @param sampling The sampling multiplier used to scale the image. Values > 1
 *  will downscale the image. Values between 0 and 1 will upscale the image.
 */
@RequiresApi(18)
class BlurTransformation @JvmOverloads constructor(
    private val context: Context,
    private val radius: Float = DEFAULT_RADIUS,
    private val sampling: Float = DEFAULT_SAMPLING,
    override val cacheKey: String = "${BlurTransformation::class.java.name}-$radius-$sampling"
) : Transformation() {

    init {
        require(radius in 0.0..25.0) { "radius must be in [0, 25]." }
        require(sampling > 0) { "sampling must be > 0." }
    }


    override suspend fun transform(input: coil3.Bitmap, size: Size): coil3.Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 计算缩放后的尺寸，避免为 0
        val scaledWidth = (input.width / sampling).toInt().coerceAtLeast(1)
        val scaledHeight = (input.height / sampling).toInt().coerceAtLeast(1)

        // 不再使用 BitmapPool，直接创建目标位图；config 以输入位图为准，兜底 ARGB_8888
        val config = input.config ?: Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(scaledWidth, scaledHeight, config)

        // 先把原图按采样比例绘制到 output
        output.applyCanvas {
            scale(1 / sampling, 1 / sampling)
            drawBitmap(input, 0f, 0f, paint)
        }

        // 使用 RenderScript 做高斯模糊
        var script: RenderScript? = null
        var tmpInt: Allocation? = null
        var tmpOut: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            script = RenderScript.create(context)
            tmpInt = Allocation.createFromBitmap(
                script,
                output,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            tmpOut = Allocation.createTyped(script, tmpInt.type)
            blur = ScriptIntrinsicBlur.create(script, Element.U8_4(script))
            blur.setRadius(radius)
            blur.setInput(tmpInt)
            blur.forEach(tmpOut)
            tmpOut.copyTo(output)
        } finally {
            script?.destroy()
            tmpInt?.destroy()
            tmpOut?.destroy()
            blur?.destroy()
        }

        return output
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BlurTransformation &&
                context == other.context &&
                radius == other.radius &&
                sampling == other.sampling
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + sampling.hashCode()
        return result
    }

    override fun toString(): String {
        return "BlurTransformation(context=$context, radius=$radius, sampling=$sampling)"
    }

    private companion object {
        private const val DEFAULT_RADIUS = 10f
        private const val DEFAULT_SAMPLING = 1f
    }
}
