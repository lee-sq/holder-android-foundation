package com.holderzone.widget.edit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holderzone.widget.edit.transformation.InputFilterMaxLength
import com.holderzone.widget.spacer.HorizontalSpacer

@Composable
fun CommonTextField(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    fontSize: TextUnit = 16.sp,
    fontColor: Color = Color(0xCCFFFFFF),
    textStyle: TextStyle = TextStyle.Default,
    maxLength: Int? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    hint: String? = null,
    hintColor: Color = Color(0x4DFFFFFF),
    contentAlignment: Alignment.Vertical = Alignment.CenterVertically,
    backgroundColor: Color = Color(0x33FFFFFF),
    backgroundShape: Shape = RoundedCornerShape(4.dp),
    horizontalPadding: Dp = 20.dp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autoFocus: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color(0xFF222222)),
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    decorator: TextFieldDecorator? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val combinedModifier = modifier.then(Modifier.height(50.dp).focusRequester(focusRequester).focusTarget())
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val inputTrans = maxLength?.let { max ->
        InputFilterMaxLength(max).let { maxLengthFilter ->
            inputTransformation?.then(maxLengthFilter) ?: maxLengthFilter
        }
    } ?: inputTransformation
    BasicTextField(
        state = state,
        modifier = combinedModifier,
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTrans,
        textStyle = textStyle.copy(color = fontColor, fontSize = fontSize),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        outputTransformation = outputTransformation,
        scrollState = scrollState,
        decorator = decorator ?: TextFieldDecorator { innerTextField ->
            CommonTextFieldContent(
                innerTextField = innerTextField,
                valueIsEmpty = { state.text.isEmpty() },
                fontSize = fontSize,
                textStyle = textStyle,
                maxLines = if (lineLimits is TextFieldLineLimits.MultiLine) lineLimits.maxHeightInLines else 1,
                hint = hint,
                hintColor = hintColor,
                contentAlignment = contentAlignment,
                backgroundColor = backgroundColor,
                backgroundShape = backgroundShape,
                horizontalPadding = horizontalPadding,
                leading = leading,
                trailing = trailing,
            )
        }
    )
}

//放置背景等布局,并放置基础输入框
@Composable
private inline fun CommonTextFieldContent(
    innerTextField: @Composable () -> Unit,
    valueIsEmpty: () -> Boolean,
    fontSize: TextUnit,
    textStyle: TextStyle,
    maxLines: Int,
    hint: String?,
    hintColor: Color,
    contentAlignment: Alignment.Vertical,
    backgroundColor: Color,
    backgroundShape: Shape,
    horizontalPadding: Dp,
    noinline leading: (@Composable RowScope.() -> Unit)?,
    noinline trailing: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(backgroundColor, backgroundShape)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            HorizontalSpacer(horizontalPadding)
        }
        Box(
            Modifier
                .weight(1f)
                .align(contentAlignment)
                .let {
                    if (contentAlignment != Alignment.CenterVertically) {
                        it.padding(vertical = horizontalPadding / 2)
                    } else {
                        it
                    }
                }
        ) {
            if (valueIsEmpty() && !hint.isNullOrEmpty()) {
                Text(
                    text = hint,
                    style = textStyle.copy(color = hintColor, fontSize = fontSize),
                    maxLines = maxLines,
                )
            }
            innerTextField()
        }
        if (trailing != null) {
            HorizontalSpacer(horizontalPadding)
            trailing()
        }
    }
}
