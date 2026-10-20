package com.dateroem.woodsales.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.R
import com.dateroem.woodsales.ui.common.animationsOff
import com.dateroem.woodsales.ui.common.drawEntranceScrim
import com.dateroem.woodsales.ui.common.drawFogBand
import com.dateroem.woodsales.ui.common.drawGreenWash
import com.dateroem.woodsales.ui.common.drawPhotoCover
import com.dateroem.woodsales.ui.common.drawRestingMist
import com.dateroem.woodsales.ui.common.entranceProgress
import com.dateroem.woodsales.ui.common.rememberEntrancePhoto
import com.dateroem.woodsales.ui.common.rememberFrameClock
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.DateroemWordmark
import kotlinx.coroutines.delay

// The entrance, in milliseconds from the first frame.
private const val OAK_RISE = 0f           // the old oak rises out of the dark, like first light
private const val OAK_RISE_MS = 1300f
private const val FOG_ROLLS_IN = 1800f    // mist rolls in and hides the oak
private const val FOG_ROLL_MS = 900f
private const val FOREST_UNDER_FOG = 2500f
private const val FOG_CLEARS = 2800f      // ... and clears over the misty pine forest
private const val FOG_CLEAR_MS = 1000f
private const val BRAND_SCRIM = 3300f
private const val EMBLEM_IN = 3500f
private const val WORDMARK_IN = 4000f
private const val TAGLINE_IN = 4450f
private const val GREEN_WASH = 4900f      // the app's greens wash in, as behind sign-in
private const val GREEN_WASH_MS = 900f
private const val ENTRANCE_MS = 5800L     // ENTRANCE_END_MS: sign-in's mist carries on from here
// With the phone's animations off: the finished picture (as at STILL_AT), briefly.
private const val STILL_AT = 6000f
private const val STILL_MS = 1500L

/**
 * "the board has seen it wise to use a real forest instead of the drawn one
 * we have to act as an entry" (18 October 2026). Two real photographs in
 * place of the drawn forest: the old oak rising out of the dark, mist
 * rolling in, and clearing over the misty pine forest, where the company
 * emblem and the wood wordmark settle in - then sign-in (LoginScreen)
 * carries on in the same forest. A tap skips it. Plays every time the app
 * is opened afresh, and again when it comes back after a few minutes away
 * (AppRoot, SessionManager.shouldShowSplash) - not once per install.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val oak = rememberEntrancePhoto(R.drawable.entrance_oak)
    val forest = rememberEntrancePhoto(R.drawable.entrance_forest)
    val fog = rememberEntrancePhoto(R.drawable.entrance_fog)
    val finish by rememberUpdatedState(onFinish)
    val finished = remember { booleanArrayOf(false) }
    val done = {
        if (!finished[0]) {
            finished[0] = true
            finish()
        }
    }

    // The timeline waits for the oak (decoded off the main thread) so the
    // first thing seen is the photo rising, not a photo popping in late.
    val clockRunning = !still && oak != null
    val clock = rememberFrameClock(running = clockRunning)
    val t = if (still) STILL_AT else clock

    LaunchedEffect(still, clockRunning) {
        if (still) {
            delay(STILL_MS)
            done()
        } else if (clockRunning) {
            delay(ENTRANCE_MS)
            done()
        }
    }
    // Never stuck on a dark screen: if the photos can't be loaded at all,
    // move on after the usual length.
    LaunchedEffect(Unit) {
        delay(ENTRANCE_MS + 2500L)
        done()
    }

    // Shot one: the oak, slowly approached.
    val oakAlpha = entranceProgress(t, OAK_RISE, OAK_RISE_MS)
    val oakZoom = 1f + 0.10f * entranceProgress(t, 0f, 3000f)
    // Mist rolling in, then clearing.
    val veil = entranceProgress(t, FOG_ROLLS_IN, FOG_ROLL_MS) * (1f - entranceProgress(t, FOG_CLEARS, FOG_CLEAR_MS))
    // Shot two: the forest, the camera slowly rising out of the mist.
    val showForest = t >= FOREST_UNDER_FOG
    val forestRise = entranceProgress(t, FOREST_UNDER_FOG, 3300f)
    val forestZoom = 1.10f - 0.07f * forestRise
    val forestShift = 0.03f * (1f - forestRise)
    // The brand.
    val scrim = entranceProgress(t, BRAND_SCRIM, 1000f)
    val emblemIn = entranceProgress(t, EMBLEM_IN, 800f)
    val wordmarkIn = entranceProgress(t, WORDMARK_IN, 600f)
    val taglineIn = entranceProgress(t, TAGLINE_IN, 550f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            .pointerInput(Unit) { detectTapGestures { done() } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!showForest && oak != null) {
                drawPhotoCover(oak, alpha = oakAlpha, zoom = oakZoom, pivotY = 0.6f)
                // A little ground mist across the meadow.
                if (fog != null) {
                    drawFogBand(fog, t, centerY = 0.88f, bandHeight = 0.26f, alpha = 0.40f * oakAlpha, speedDp = 9f)
                }
            }
            if (showForest && forest != null) {
                drawPhotoCover(forest, zoom = forestZoom, pivotY = 0.45f, shiftY = forestShift)
            }
            if (fog != null) {
                // The resting mist over the forest's tree line - the same as behind sign-in.
                if (showForest) drawRestingMist(fog, t)
                // The mist that rolls in between the shots: thick, fast banks
                // over a soft grey-green veil.
                if (veil > 0f) {
                    drawRect(Color(0xFF8A968F).copy(alpha = 0.88f * veil))
                    drawFogBand(fog, t * 2.5f, centerY = 0.40f, bandHeight = 0.9f, alpha = veil, speedDp = 24f)
                    drawFogBand(fog, t * 2.5f + 3000f, centerY = 0.70f, bandHeight = 0.8f, alpha = veil, speedDp = 36f)
                }
            } else if (veil > 0f) {
                drawRect(Color(0xFF8A968F).copy(alpha = 0.88f * veil))
            }
            drawEntranceScrim(0.62f * scrim)
            // A soft shade behind the emblem so its gold reads against the mist.
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF050B07).copy(alpha = 0.45f * scrim), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.minDimension * 0.75f
                )
            )
            // The app's greens washing in: sign-in's colours, so the
            // dissolve into sign-in (AppRoot) doesn't jump.
            drawGreenWash(entranceProgress(t, GREEN_WASH, GREEN_WASH_MS))
        }

        val emblemSize = min(maxWidth * 0.46f, 200.dp)
        val wordmarkWidth = min(maxWidth * 0.74f, 320.dp)
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.dateroem_emblem),
                contentDescription = "Dateroem Woods",
                modifier = Modifier
                    .size(emblemSize)
                    .graphicsLayer {
                        alpha = emblemIn
                        val s = 0.86f + 0.14f * emblemIn
                        scaleX = s
                        scaleY = s
                    }
                    // A warm glow from the wood, a little wider than the
                    // emblem (drawn behind it, so it takes no room).
                    .drawBehind {
                        val glow = size.minDimension * 0.75f
                        drawCircle(
                            Brush.radialGradient(listOf(DateroemAccent.copy(alpha = 0.20f), Color.Transparent), center = center, radius = glow),
                            radius = glow
                        )
                    }
            )
            val rise = with(LocalDensity.current) { 10.dp.toPx() }
            DateroemWordmark(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .width(wordmarkWidth)
                    .graphicsLayer {
                        alpha = wordmarkIn
                        translationY = rise * (1f - wordmarkIn)
                    }
            )
            Text(
                text = "Growing Timber, Growing Futures",
                color = DateroemTextMuted,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer { alpha = taglineIn }
            )
        }
    }
}
