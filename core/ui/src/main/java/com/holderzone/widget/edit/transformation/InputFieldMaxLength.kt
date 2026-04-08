package com.holderzone.widget.edit.transformation

import android.util.Log
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.maxTextLength
import androidx.compose.ui.text.TextRange

data class InputFilterMaxLength(
    private val maxLength: Int
) : InputTransformation {

    init {
        require(maxLength >= 0) { "maxLength must be at least zero, was $maxLength" }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        maxTextLength = maxLength
    }

    override fun TextFieldBuffer.transformInput() {
        // 总计输入内容没有超出长度限制
        if (length <= maxLength) return

        // 输入内容超出了长度限制, 这里要分两种情况：
        // 1. 直接输入的，则返回原数据即可
        // 2. 粘贴后会导致长度超出，此时可能还可以输入部分字符，所以需要判断后截断输入

        val oldStart = originalSelection.start
        val oldEnd = originalSelection.end

        // 计算实际的旧字符数，以总字符数-被光标框选的长度（因为这部分会被替换）
        val oldCount = (originalText.length - (oldEnd - oldStart))
        val newCount = length

        // 计算这次新增了几个字符
        val inputCharCount = newCount - oldCount
        val allowCount = maxLength - oldCount
        // 允许再输入字符已经为空，则直接返回原数据
        if (allowCount <= 0) {
            revertAllChanges()
            return
        }

        try {
            // 同时粘贴了多个字符内容
            if (inputCharCount > 1) {
                // 截取应该新增的字符部分
                val newChar = asCharSequence().substring(oldStart, oldStart + allowCount)

                // 从光标起始位置开始插入新增字符（前后补全旧字符）
                val newText = buildString {
                    append(originalText, 0, oldStart)
                    append(newChar)
                    append(originalText, oldEnd, originalText.length)
                }
                replace(0, length, newText)
                selection = TextRange(oldStart + newChar.length)
            }
        } catch (e: Exception) {
            Log.d("InputFieldMaxLength", "$e")
            revertAllChanges()
        }
    }

    override fun toString(): String {
        return "InputTransformation.maxLength($maxLength)"
    }
}