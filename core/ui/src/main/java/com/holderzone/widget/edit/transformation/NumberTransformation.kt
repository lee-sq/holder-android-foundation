package com.holderzone.widget.edit.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange

/**
 * 数字文本转换，只允许输入数字，如果传入小数位>0，就是只能输入几位小数, 如果等于0那么就是只能输入整数，如果小于0则抛出异常，自动处理光标以及粘贴后的文本内容
 *
 * @param decimalPlaces 小数位数，>0表示允许小数，=0表示只允许整数，<0会抛出异常
 * @param allowNegative 是否允许负数，默认为false
 */
class NumberTransformation(
    private val decimalPlaces: Int = 2,
    private val allowNegative: Boolean = false
) : InputTransformation {

    init {
        require(decimalPlaces >= 0) { "小数位数不能小于0，当前值: $decimalPlaces" }
    }

    override fun TextFieldBuffer.transformInput() {
        val originalText = toString()
        val filteredText = filterNumericInput(originalText)

        if (originalText != filteredText) {
            // 如果文本发生了变化，需要更新内容并调整光标位置
            val cursorOffset = calculateCursorOffset(originalText, filteredText, selection.start)
            replace(0, length, filteredText)
            selection = TextRange(cursorOffset, cursorOffset)
        }
    }

    /**
     * 过滤输入，只保留符合规则的数字字符
     */
    private fun filterNumericInput(input: String): String {
        if (input.isEmpty()) return input

        val result = StringBuilder()
        var hasDecimalPoint = false
        var decimalCount = 0
        var hasNegativeSign = false

        for (i in input.indices) {
            val char = input[i]

            when {
                // 处理负号
                char == '-' && allowNegative && i == 0 && !hasNegativeSign -> {
                    hasNegativeSign = true
                    result.append(char)
                }
                // 处理数字
                char.isDigit() -> {
                    if (hasDecimalPoint) {
                        if (decimalCount < decimalPlaces) {
                            decimalCount++
                            result.append(char)
                        }
                        // 如果已达到小数位数限制，忽略后续数字
                    } else {
                        result.append(char)
                    }
                }
                // 处理小数点
                char == '.' && decimalPlaces > 0 && !hasDecimalPoint -> {
                    hasDecimalPoint = true
                    result.append(char)
                }
                // 其他字符都忽略
            }
        }

        // 处理特殊情况
        val finalResult = result.toString()
        return when {
            finalResult == "-" -> "" // 只有负号的情况
            finalResult == "." -> "" // 只有小数点的情况
            finalResult.endsWith(".") && decimalPlaces == 0 -> finalResult.dropLast(1) // 整数模式下移除末尾小数点
            else -> finalResult
        }
    }

    /**
     * 计算过滤后的光标位置
     */
    private fun calculateCursorOffset(
        original: String,
        filtered: String,
        originalCursor: Int
    ): Int {
        if (originalCursor <= 0) return 0
        if (originalCursor >= original.length) return filtered.length

        var filteredIndex = 0
        var originalIndex = 0

        // 逐字符比较，找到对应的光标位置
        while (originalIndex < originalCursor && filteredIndex < filtered.length) {
            if (original[originalIndex] == filtered[filteredIndex]) {
                filteredIndex++
            }
            originalIndex++
        }

        return filteredIndex.coerceAtMost(filtered.length)
    }
}