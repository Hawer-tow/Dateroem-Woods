package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.dateroem.woodsales.BuildConfig
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import androidx.compose.material3.Icon
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

/**
 * Product photo with visible loading/error states, instead of AsyncImage's
 * default of just staying blank on failure - a large unprocessed camera
 * photo silently failing to load (no error shown anywhere) is what made
 * "the image isn't showing" hard to diagnose in the first place.
 */
// "cant seem to get the images in the mobile app for the products" - the
// backend bakes a loopback-only absolute URL (http://127.0.0.1:8000/...)
// into every image field, meaningless on a phone. mobileServer now
// rewrites that down to a relative "media/x.jpg" (see backend_client.py);
// this resolves it against wherever the app is already reaching
// mobileServer itself (USB/adb reverse, LAN, or the ngrok tunnel) - an
// already-complete address is left untouched: http/https, and also the
// content:// and file:// addresses of a photo or video just picked on the
// phone (those used to get the server address glued in front, so a fresh
// pick couldn't be previewed). Shared with VideoPlayer and VideoThumbnail.
internal fun resolveMediaUrl(url: String): String =
    if (url.contains("://")) url
    else BuildConfig.MOBILE_SERVER_BASE_URL.trimEnd('/') + "/" + url.trimStart('/')

@Composable
fun ProductThumbnail(imageUrl: String?, modifier: Modifier = Modifier, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(DateroemSurface),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(AppIcons.TreeRings, contentDescription = null, tint = DateroemTextMuted, modifier = Modifier.size(30.dp))
        } else {
            SubcomposeAsyncImage(
                model = resolveMediaUrl(imageUrl),
                contentDescription = "Product photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DateroemAccent, strokeWidth = 2.dp) },
                error = { Icon(AppIcons.TreeRings, contentDescription = null, tint = DateroemTextMuted, modifier = Modifier.size(30.dp)) },
                success = { SubcomposeAsyncImageContent() }
            )
        }
    }
}
