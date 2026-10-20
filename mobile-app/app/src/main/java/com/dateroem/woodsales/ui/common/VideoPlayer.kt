package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * "i want to ensure the videos are seen on all sections well" - an
 * uploaded campaign video previously only ever showed as a static "Video
 * attached" badge, never actually playable anywhere in the app.
 * A thin Compose wrapper around Media3's ExoPlayer/PlayerView (the
 * standard playback stack, not a bespoke MediaPlayer setup) - one player
 * instance per composition, released on dispose so leaving the screen
 * doesn't leak it or leave audio running in the background.
 */
@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            // A saved campaign's video arrives as "media/campaigns/x.mp4"
            // (relative to the mobile server), which the player can't open
            // on its own - so editing a campaign never showed its video.
            setMediaItem(MediaItem.fromUri(resolveMediaUrl(url)))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
            }
        }
    )
}
