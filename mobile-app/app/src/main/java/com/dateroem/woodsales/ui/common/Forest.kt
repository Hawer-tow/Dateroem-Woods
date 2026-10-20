package com.dateroem.woodsales.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

// Shared with SplashScreen.kt so the forest reads as the same place across
// the entrance flow, not two different tree-drawing implementations.
data class BackgroundTree(
    val xFraction: Float,
    val scale: Float,
    val delayMs: Long = 0L,
    val tone: Color = Color(0xFF265A34),
    val swayPhase: Float = 0f
)

val defaultForest = listOf(
    BackgroundTree(0.06f, 0.55f, 0L, Color(0xFF1F4A2C), 0.0f),
    BackgroundTree(0.16f, 0.75f, 90L, Color(0xFF265A34), 1.1f),
    BackgroundTree(0.27f, 0.5f, 40L, Color(0xFF1B4026), 2.3f),
    BackgroundTree(0.74f, 0.65f, 120L, Color(0xFF265A34), 0.6f),
    BackgroundTree(0.85f, 0.5f, 60L, Color(0xFF1F4A2C), 1.8f),
    BackgroundTree(0.94f, 0.7f, 150L, Color(0xFF1B4026), 2.9f)
)

/**
 * Draws one pine-silhouette tree. [growth] is 0..1 for the splash's
 * sprout-in effect (pass 1f for an already-grown, idle scene like the
 * login background). [swayRadians] gently shears the canopy for a living,
 * breathing feel on screens where the tree isn't otherwise animating.
 */
fun DrawScope.drawPineTree(
    xFraction: Float,
    scale: Float,
    growth: Float,
    tone: Color,
    canvasSize: Size,
    swayRadians: Float = 0f
) {
    if (growth <= 0f) return
    val baseX = canvasSize.width * xFraction
    val baseY = canvasSize.height - 4.dp.toPx()
    val unit = 60.dp.toPx() * scale * growth

    drawRect(
        color = Color(0xFF4A3320),
        topLeft = Offset(baseX - unit * 0.08f, baseY - unit * 1.3f),
        size = Size(unit * 0.16f, unit * 1.3f)
    )
    listOf(1.3f to 2.2f, 1.05f to 1.85f, 0.85f to 1.5f).forEach { (top, width) ->
        val sway = swayRadians * top // higher layers sway more, like a real canopy
        val path = Path().apply {
            moveTo(baseX + unit * sway, baseY - unit * top)
            lineTo(baseX - unit * width * 0.5f, baseY - unit * (top - 0.85f))
            lineTo(baseX + unit * width * 0.5f, baseY - unit * (top - 0.85f))
            close()
        }
        drawPath(path, color = tone)
    }
}
