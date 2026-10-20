package com.dateroem.woodsales.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.SoftReference
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * "the board has seen it wise to use a real forest instead of the drawn one
 * we have to act as an entry" (18 October 2026) - the entrance
 * (SplashScreen) and sign-in (LoginScreen) stand in two real photographs,
 * the old oak and the misty pine forest, with mist drifting across them.
 *
 * res/drawable-nodpi/entrance_oak.webp, entrance_forest.webp: the board's
 * pictures (kept in fathersProject/brand/entrance photos), 1200 x 1800,
 * graded to the app's dark forest colours. entrance_fog.webp: a soft mist
 * texture that tiles left to right. Both screens draw them with the helpers
 * below so they read as the same place.
 */

object EntrancePhotos {
    // Decoded once, off the main thread. Kept softly, so sign-in shows the
    // forest the instant the entrance hands over to it (no dark gap), while
    // the system can still reclaim the memory once the app is in use.
    private val cache = mutableMapOf<Int, SoftReference<ImageBitmap>>()

    fun cached(@DrawableRes id: Int): ImageBitmap? = synchronized(cache) { cache[id]?.get() }

    suspend fun load(context: Context, @DrawableRes id: Int): ImageBitmap? = cached(id) ?: withContext(Dispatchers.IO) {
        // A phone that can't spare the memory just gets the plain dark
        // background - the entrance still plays.
        runCatching {
            BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
                ?.asImageBitmap()
        }.getOrNull()
    }?.also { image -> synchronized(cache) { cache[id] = SoftReference(image) } }
}

/** The photo once it's decoded (null until then, or if it can't be). */
@Composable
fun rememberEntrancePhoto(@DrawableRes id: Int): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(id) { mutableStateOf(EntrancePhotos.cached(id)) }
    LaunchedEffect(id) {
        if (image == null) image = EntrancePhotos.load(context.applicationContext, id)
    }
    return image
}

/** True when the phone's animations are turned off (Developer options / Accessibility "Remove animations"). */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Milliseconds since the screen appeared, ticking every frame - drives the
 * entrance's timeline and the drifting mist. Stays at 0 while [running] is
 * false (animations off).
 */
@Composable
fun rememberFrameClock(running: Boolean = true): Float {
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { clock = (it - start).toFloat() }
        }
    }
    return clock
}

/**
 * Draws [photo] filling the whole area (what doesn't fit is cropped),
 * zoomed by [zoom] around the point ([pivotX], [pivotY]) and moved down by
 * [shiftY] - all fractions of the area. Drawn with transforms rather than
 * rounded pixel positions, so a slow zoom glides instead of stepping.
 */
fun DrawScope.drawPhotoCover(
    photo: ImageBitmap,
    alpha: Float = 1f,
    zoom: Float = 1f,
    pivotX: Float = 0.5f,
    pivotY: Float = 0.5f,
    shiftY: Float = 0f
) {
    if (alpha <= 0f) return
    val fit = max(size.width / photo.width, size.height / photo.height)
    val pivot = Offset(size.width * pivotX, size.height * pivotY)
    val left0 = (size.width - photo.width * fit) / 2f
    val top0 = (size.height - photo.height * fit) / 2f
    val left = pivot.x + (left0 - pivot.x) * zoom
    val top = pivot.y + (top0 - pivot.y) * zoom + size.height * shiftY
    withTransform({
        translate(left, top)
        scale(fit * zoom, fit * zoom, pivot = Offset.Zero)
    }) {
        drawImage(photo, alpha = alpha.coerceAtMost(1f))
    }
}

/**
 * One band of mist drifting to the left: the [fog] texture repeated across
 * the width, [bandHeight] tall (a fraction of the height) and centred at
 * [centerY]. [speedDp] is how far it drifts each second; [timeMs] the
 * clock. [stretch] widens the texture - mist lies in long banks;
 * [flipX] and [flipY] turn it over, so layers never show the same shapes.
 *
 * Drawn with one repeating shader, so the copies meet edge to edge ("i can
 * see some straight lines ... also moving horizontally", 20 October 2026 -
 * they used to be separate copies overlapping by a pixel, which doubled the
 * mist into a thin bright line at every join).
 */
fun DrawScope.drawFogBand(
    fog: ImageBitmap,
    timeMs: Float,
    centerY: Float,
    bandHeight: Float,
    alpha: Float,
    speedDp: Float,
    stretch: Float = 1.6f,
    flipX: Boolean = false,
    flipY: Boolean = false
) {
    if (alpha <= 0f) return
    val height = size.height * bandHeight
    val scaleY = height / fog.height
    val scaleX = scaleY * stretch
    val tileWidth = fog.width * scaleX
    val travelled = (timeMs / 1000f) * speedDp.dp.toPx()
    val top = size.height * centerY - height / 2f
    val shader = ImageShader(fog, TileMode.Repeated, TileMode.Clamp)
    shader.setLocalMatrix(
        android.graphics.Matrix().apply {
            // Flip within the texture's own box, then size it and slide it.
            setScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, fog.width / 2f, fog.height / 2f)
            postScale(scaleX, scaleY)
            postTranslate(-(travelled % tileWidth), top)
        }
    )
    drawRect(ShaderBrush(shader), topLeft = Offset(0f, top), size = Size(size.width, height), alpha = alpha.coerceAtMost(1f))
}

/** Darkens the photo for legibility: deeper at the top and bottom, lighter through the middle. */
fun DrawScope.drawEntranceScrim(strength: Float) {
    if (strength <= 0f) return
    val shade = Color(0xFF050B07)
    drawRect(
        Brush.verticalGradient(
            0f to shade.copy(alpha = (0.85f * strength).coerceAtMost(1f)),
            0.45f to shade.copy(alpha = 0.55f * strength),
            1f to shade.copy(alpha = (0.95f * strength).coerceAtMost(1f))
        )
    )
}

/**
 * When the entrance ends: sign-in's mist carries on from this moment, so the
 * hand-over from the entrance to sign-in doesn't jump (19 October 2026).
 */
const val ENTRANCE_END_MS = 5800f

/**
 * The mist resting over the forest's tree line, as real mist moves ("allow
 * it to feel even more real not too much just perfect ... make it more
 * visible", 20 October 2026): three layers at different depths - the far
 * one widest and slowest, the near one quicker - each slowly thickening and
 * thinning and rising and settling a little, out of step with the others.
 * The same in the entrance's last moments and behind sign-in (the desktop
 * app draws the same layers).
 */
fun DrawScope.drawRestingMist(fog: ImageBitmap, timeMs: Float, alpha: Float = 1f) {
    val s = timeMs / 1000f
    MIST_LAYERS.forEachIndexed { i, layer ->
        val breathe = 0.80f + 0.20f * sin(2f * PI.toFloat() * s / layer.breathSeconds + layer.phase)
        val rise = 0.008f * sin(2f * PI.toFloat() * s / layer.driftSeconds + layer.phase * 1.7f)
        drawFogBand(
            fog, timeMs + i * 7000f, centerY = layer.centerY + rise, bandHeight = layer.bandHeight,
            alpha = layer.strength * breathe * alpha, speedDp = layer.speedDp, stretch = layer.stretch,
            flipX = layer.flipX, flipY = layer.flipY
        )
    }
}

private class MistLayer(
    val centerY: Float, val bandHeight: Float, val strength: Float, val speedDp: Float, val stretch: Float,
    val flipX: Boolean, val flipY: Boolean, val breathSeconds: Float, val driftSeconds: Float, val phase: Float
)

private val MIST_LAYERS = listOf(
    MistLayer(0.46f, 0.50f, 0.32f, 6f, 2.6f, flipX = true, flipY = false, breathSeconds = 23f, driftSeconds = 31f, phase = 0f), // far: a wide, soft bank
    MistLayer(0.50f, 0.42f, 0.62f, 12f, 1.6f, flipX = false, flipY = false, breathSeconds = 17f, driftSeconds = 27f, phase = 1.7f), // middle
    MistLayer(0.64f, 0.30f, 0.38f, 18f, 1.9f, flipX = true, flipY = true, breathSeconds = 13f, driftSeconds = 19f, phase = 3.1f), // near: thinner wisps, quicker
)

/**
 * The app's own greens washing over the photo - the colours of every screen
 * after sign-in - so the photo and the app meet halfway ("let it feel like a
 * transition graceful", 19 October 2026). Washes in at the end of the
 * entrance and stays behind sign-in.
 */
fun DrawScope.drawGreenWash(strength: Float) {
    if (strength <= 0f) return
    drawRect(
        Brush.verticalGradient(
            0f to DateroemBackground.copy(alpha = 0.55f * strength),
            0.5f to DateroemBackground.copy(alpha = 0.30f * strength),
            1f to DateroemSurface.copy(alpha = 0.62f * strength)
        )
    )
}

/** 0..1 progress through [start, start + duration], eased in and out. */
fun entranceProgress(timeMs: Float, start: Float, duration: Float): Float {
    val x = ((timeMs - start) / duration).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
