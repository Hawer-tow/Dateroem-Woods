package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import kotlinx.coroutines.delay

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
            // The menu's photo is drawn as soon as Home opens; a first try
            // that fails (no connection yet) used to leave the letter there
            // for good while My profile, loading later, showed the photo
            // (24 October 2026). A failed photo is tried again - after 2, 4,
            // 6 ... seconds, then every 30 - until it loads.
            var attempt by remember(photoUrl) { mutableIntStateOf(0) }
            val context = LocalContext.current
            val request = remember(photoUrl, attempt) {
                ImageRequest.Builder(context)
                    .data(resolveMediaUrl(photoUrl))
                    .setParameter("attempt", attempt, memoryCacheKey = null)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
                error = {
                    initial()
                    LaunchedEffect(attempt) {
                        delay(minOf(2_000L * (attempt + 1), 30_000L))
                        attempt += 1
                    }
                }
            )
        }
    }
}
