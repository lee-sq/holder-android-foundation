package com.holderzone.widget.edit.transformation

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

class PasswordOutTransformation(
    private val mask: Char = '•',
    var isVisible: Boolean = true
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        if (isVisible) {
            return
        }
        for (i in 0 until length) {
            replace(i, i + 1, mask.toString())
        }
    }
}