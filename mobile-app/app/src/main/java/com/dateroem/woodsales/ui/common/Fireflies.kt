package com.dateroem.woodsales.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.sin

data class Firefly(val xFraction: Float, val startYFraction: Float, val periodMs: Int, val phase: Float, val driftPx: Float)

// Shared with LoginScreen.kt so the "light drifting through the forest"
// feel is consistent everywhere it appears, not two slightly different
// implementations.
val defaultFireflies = listOf(
    Firefly(0.15f, 0.75f, 5200, 0.0f, 40f),
    Firefly(0.35f, 0.55f, 6300, 1.7f, 55f),
    Firefly(0.62f, 0.68f, 4800, 3.1f, 35f),
    Firefly(0.8f, 0.45f, 7000, 0.9f, 60f),
    Firefly(0.5f, 0.35f, 5900, 2.4f, 45f)
)

/**
 * Draws drifting, fading light points. [driftClockMs] should be an
 * ever-increasing value (e.g. from an infinite Compose animation) driving
 * the loop - each firefly rises and fades on its own period/phase so they
 * don't all pulse in sync.
 */
fun DrawScope.drawFireflies(size: Size, driftClockMs: Float, fireflies: List<Firefly> = defaultFireflies, color: Color) {
    fireflies.forEach { fly ->
        val t = ((driftClockMs + fly.phase * 1000) % fly.periodMs) / fly.periodMs
        val y = size.height * fly.startYFraction - t * fly.driftPx
        val alpha = sin(t * Math.PI).toFloat().coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = alpha * 0.8f),
            radius = 2.5.dp.toPx(),
            center = Offset(size.width * fly.xFraction, y)
        )
    }
}
