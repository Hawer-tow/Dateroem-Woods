package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An error message in a box of its own: tinted, at most about four lines
 * tall, and scrolling inside itself when the message is longer, with a thin
 * bar showing there is more. "some errors are kind of long ... some go over
 * other thins so ... if the error apperas the space can e.g be scrollable
 * allowing one read the error not going above other contents"
 * (24 October 2026) - a long message no longer pushes the screen around or
 * slides under the floating button.
 */
@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    small: Boolean = false,
    color: Color = MaterialTheme.colorScheme.error
) {
    val scroll = rememberScrollState()
    val size = if (small) 12.sp else fontSize
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (small) 84.dp else 104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .drawWithContent {
                drawContent()
                if (scroll.maxValue > 0) {
                    val visible = this.size.height
                    val thumb = (visible * visible / (visible + scroll.maxValue)).coerceAtLeast(14.dp.toPx())
                    val top = (visible - thumb) * scroll.value / scroll.maxValue
                    drawRoundRect(
                        color = color.copy(alpha = 0.55f),
                        topLeft = Offset(this.size.width - 5.dp.toPx(), top),
                        size = Size(3.dp.toPx(), thumb),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }
            .verticalScroll(scroll)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(message, color = color, fontSize = size, lineHeight = size * 1.35f)
    }
}
