package com.holderzone.hardware.camera.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.holderzone.hardware.camera.PreviewHost

/**
 * Default View-based host for camera preview content.
 *
 * The SDK attaches a single backend-managed preview view into this container.
 */
class CameraPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), PreviewHost {

    private var attachedPreview: View? = null

    override val previewContext: Context
        get() = context

    override fun attachPreview(view: View) {
        if (attachedPreview === view && view.parent === this) {
            return
        }
        detachCurrentPreview()
        (view.parent as? ViewGroup)?.removeView(view)
        attachedPreview = view
        addView(
            view,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
        )
    }

    override fun detachPreview(view: View) {
        if (attachedPreview === view) {
            removeView(view)
            attachedPreview = null
        } else {
            removeView(view)
        }
    }

    /**
     * Clears any preview view currently attached by the SDK.
     */
    fun releasePreview() {
        detachCurrentPreview()
    }

    override fun onDetachedFromWindow() {
        detachCurrentPreview()
        super.onDetachedFromWindow()
    }

    private fun detachCurrentPreview() {
        attachedPreview?.let(::removeView)
        attachedPreview = null
    }
}
