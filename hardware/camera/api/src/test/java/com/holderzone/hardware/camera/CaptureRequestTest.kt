package com.holderzone.hardware.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CaptureRequestTest {

    @Test
    fun preferStill_defaultsToSdkManagedOutput() {
        val request = CaptureRequest.PreferStill()

        assertNull(request.outputFile)
    }

    @Test
    fun previewSnapshot_keepsCallerProvidedOutputFile() {
        val target = File("custom/output/path.jpg")
        val request = CaptureRequest.PreviewSnapshot(outputFile = target)

        assertEquals(target, request.outputFile)
    }
}
