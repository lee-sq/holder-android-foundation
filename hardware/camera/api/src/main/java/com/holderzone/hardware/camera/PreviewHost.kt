package com.holderzone.hardware.camera

import android.content.Context
import android.view.View

/**
 * UI container abstraction used by the SDK to render preview content.
 */
interface PreviewHost {

    /**
     * Context used to inflate preview views.
     */
    val previewContext: Context

    /**
     * Attaches the backend preview view to the host container.
     */
    fun attachPreview(view: View)

    /**
     * Removes a previously attached preview view from the host container.
     */
    fun detachPreview(view: View)
}
