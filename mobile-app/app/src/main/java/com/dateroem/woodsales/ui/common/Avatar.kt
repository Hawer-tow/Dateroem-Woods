package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText

/** The account's profile photo in a wood ring, or the first letter of the name until there is one. */
@Composable
fun Avatar(name: String?, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DateroemSurface)
            .border(if (size >= 64.dp) 3.dp else 1.5.dp, DateroemAccent, CircleShape)
    ) {
        val initial: @Composable () -> Unit = {
            Text(
                (name?.trim()?.firstOrNull() ?: '?').uppercase(),
                color = DateroemText,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
        if (photoUrl.isNullOrBlank()) {
            initial()
        } else {
            SubcomposeAsyncImage(
                model = resolveMediaUrl(photoUrl),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
                error = { initial() }
            )
        }
    }
}
