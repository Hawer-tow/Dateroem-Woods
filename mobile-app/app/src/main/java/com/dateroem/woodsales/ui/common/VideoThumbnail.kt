package com.dateroem.woodsales.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private sealed interface FrameState {
    object Loading : FrameState
    class Ready(val image: ImageBitmap) : FrameState
    object Failed : FrameState
}

/**
 * "i should see a minimized view of the video like the first part" - a
 * still from the start of a campaign's video, so campaigns can be told
 * apart at a glance in lists (every video used to show the same badge).
 * Android's own MediaMetadataRetriever reads just enough of the file to
 * draw that one frame, and the frame is kept - in memory and as a small
 * JPEG in the app's cache - so each video is only fetched once.
 */
@Composable
fun VideoThumbnail(videoUrl: String, modifier: Modifier = Modifier, cornerRadius: Dp = 8.dp) {
    val context = LocalContext.current
    val state by produceState<FrameState>(
        initialValue = VideoFrames.cached(videoUrl)?.let { FrameState.Ready(it.asImageBitmap()) } ?: FrameState.Loading,
        videoUrl
    ) {
        if (value is FrameState.Loading) {
            value = VideoFrames.load(context.applicationContext, videoUrl)
                ?.let { FrameState.Ready(it.asImageBitmap()) }
                ?: FrameState.Failed
        }
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)).background(DateroemSurface),
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            FrameState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = DateroemAccent,
                strokeWidth = 2.dp
            )
            FrameState.Failed -> Icon(
                AppIcons.Video,
                contentDescription = "Video",
                tint = DateroemTextMuted,
                modifier = Modifier.size(24.dp)
            )
            is FrameState.Ready -> {
                Image(
                    current.image,
                    contentDescription = "Video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // A small play mark, so a still from a video isn't taken for a photo.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

private object VideoFrames {
    // A little under a second in: phone videos often open on a dark frame.
    private const val FRAME_AT_US = 800_000L
    private const val LONGEST_SIDE = 320

    private val memory = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(videoUrl: String): Bitmap? = memory.get(videoUrl)

    suspend fun load(context: Context, videoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(videoUrl)?.let { return@withContext it }
        // Keyed by the address as the server gave it, so the same still
        // serves whether the phone reaches the server over USB or the tunnel.
        val file = File(File(context.cacheDir, "video_frames").apply { mkdirs() }, sha1(videoUrl) + ".jpg")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let {
                memory.put(videoUrl, it)
                return@withContext it
            }
        }
        val url = resolveMediaUrl(videoUrl)
        val retriever = MediaMetadataRetriever()
        try {
            if (url.startsWith("content://") || url.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(url))
            } else {
                retriever.setDataSource(url, hashMapOf("ngrok-skip-browser-warning" to "true"))
            }
            val frame = retriever.getFrameAtTime(FRAME_AT_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext null
            val small = scaleDown(frame)
            runCatching { file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
            memory.put(videoUrl, small)
            small
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(frame: Bitmap): Bitmap {
        val longest = maxOf(frame.width, frame.height)
        if (longest <= LONGEST_SIDE) return frame
        val scale = LONGEST_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            frame,
            (frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
