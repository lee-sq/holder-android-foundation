package com.holderzone.widget.toast

import androidx.compose.ui.graphics.Color
import com.holderzone.core.ui.R
import com.holderzone.widget.theme.black
import com.holderzone.widget.theme.greenE65AD532
import com.holderzone.widget.theme.redE6FF7070
import com.holderzone.widget.theme.whiteFFFFFFFF

interface IToastProperty {
    fun getResourceId(): Int?
    fun getBackgroundColor(): Color
    fun getBorderColor(): Color
    fun getTextColor(): Color
}

class Error : IToastProperty {
    override fun getResourceId(): Int = R.drawable.icon_toast_error

    override fun getBackgroundColor(): Color = redE6FF7070

    override fun getBorderColor(): Color = redE6FF7070

    override fun getTextColor(): Color = whiteFFFFFFFF
}

class Success : IToastProperty {
    override fun getResourceId(): Int = R.drawable.icon_toast_success

    override fun getBackgroundColor(): Color = greenE65AD532

    override fun getBorderColor(): Color = greenE65AD532

    override fun getTextColor(): Color = whiteFFFFFFFF
}

class Info : IToastProperty {
    override fun getResourceId(): Int? = null

    override fun getBackgroundColor(): Color = getBorderColor()

    override fun getBorderColor(): Color = black.copy(alpha = 0.6f)

    override fun getTextColor(): Color = whiteFFFFFFFF
}
