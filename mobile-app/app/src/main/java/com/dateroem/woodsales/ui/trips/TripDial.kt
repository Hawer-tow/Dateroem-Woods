package com.dateroem.woodsales.ui.trips

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// "if a trip has not been selected in the trip selector only 6 trips are
// still shown" - this was a hardcoded 7 slots ("None" + trips 1-6), so any
// trip past #6 was physically unreachable on the dial no matter how many
// existed. Slot count now follows the real trip list instead.

// "in the bottom middle of the app i want to add a little button set... it
// should be some how transparent so that it doesnt feel invasive." Sits as
// the Scaffold's center FAB in HomeShell so it floats over every screen.
@Composable
fun TripDialTriggerButton(activeSlotLabel: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(DateroemSurface.copy(alpha = 0.55f))
            .border(1.dp, DateroemAccent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(activeSlotLabel, color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

private fun angleOfTouch(pos: Offset, center: Offset): Float {
    val dx = pos.x - center.x
    val dy = pos.y - center.y
    return Math.toDegrees(atan2(dx, -dy).toDouble()).toFloat()
}

private fun normalizeDelta(delta: Float): Float = when {
    delta > 180f -> delta - 360f
    delta < -180f -> delta + 360f
    else -> delta
}

// Shortest-path target for the same visual angle as rawTarget, relative to current.
private fun shortestTarget(current: Float, rawTarget: Float): Float {
    val diff = normalizeDelta(((rawTarget - current) % 360f + 360f) % 360f)
    return current + diff
}

private fun slotIndexFor(rotation: Float, slotCount: Int, stepDegrees: Float): Int {
    val raw = (-rotation / stepDegrees).roundToInt() % slotCount
    return ((raw % slotCount) + slotCount) % slotCount
}

/**
 * The wood-dial popup itself - "a compass like selector which pop up one
 * can rotate from trip number 1 to 6 ... or none ... wood like rotation
 * with sections partitioned to numbers up to none". Drag anywhere on the
 * wheel to spin it, or tap a number directly; either way the slot that
 * lands under the fixed top pointer becomes the active trip.
 */
@Composable
fun TripDialPicker(trips: List<JsonMap>, currentTripId: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    // "None" plus one slot per real trip - always exactly enough to reach
    // every trip, never capped at a fixed count.
    val slotCount = trips.size + 1
    val stepDegrees = 360f / slotCount

    val currentIndex = remember(trips, currentTripId) {
        if (currentTripId == null) 0 else {
            val idx = trips.indexOfFirst { (it["tripId"] as? Number)?.toInt() == currentTripId }
            if (idx < 0) 0 else idx + 1
        }
    }

    val dialSizePx = with(LocalDensity.current) { 260.dp.toPx() }
    var centerPx by remember { mutableStateOf(Offset(dialSizePx / 2f, dialSizePx / 2f)) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    val rotation = remember { Animatable(-currentIndex * stepDegrees) }
    var settledIndex by remember { mutableStateOf(currentIndex) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun isActive(index: Int) = index == 0 || index <= trips.size

    fun settleTo(index: Int) {
        val target = shortestTarget(rotation.value, -index * stepDegrees)
        scope.launch {
            rotation.animateTo(target, animationSpec = spring(dampingRatio = 0.7f))
            if (isActive(index)) {
                settledIndex = index
            } else {
                // Landed on a slot with no trip yet - snap back, nothing selected.
                settleTo(settledIndex)
            }
        }
    }

    LaunchedEffect(closing) {
        if (closing) {
            delay(220)
            onSelect(if (settledIndex == 0) null else (trips[settledIndex - 1]["tripId"] as? Number)?.toInt())
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(DateroemBackground, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text("Pick the active trip", color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Drag to spin, or tap a number",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .onGloballyPositioned { coords ->
                        centerPx = Offset(coords.size.width / 2f, coords.size.height / 2f)
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos -> lastTouchAngle = angleOfTouch(pos, centerPx) },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentAngle = angleOfTouch(change.position, centerPx)
                                val delta = normalizeDelta(currentAngle - lastTouchAngle)
                                lastTouchAngle = currentAngle
                                scope.launch { rotation.snapTo(rotation.value + delta) }
                            },
                            onDragEnd = { settleTo(slotIndexFor(rotation.value, slotCount, stepDegrees)) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                WoodDialFace(rotation = rotation.value, slotCount = slotCount, stepDegrees = stepDegrees)

                for (index in 0 until slotCount) {
                    val angleRad = Math.toRadians((index * stepDegrees + rotation.value).toDouble())
                    val radiusDp = 96.dp
                    val xDp = radiusDp * sin(angleRad).toFloat()
                    val yDp = -radiusDp * cos(angleRad).toFloat()
                    val active = isActive(index)
                    val label = if (index == 0) "None" else "$index"

                    Box(
                        modifier = Modifier
                            .offset(x = xDp, y = yDp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (active) DateroemAccent.copy(alpha = 0.9f) else DateroemSurface.copy(alpha = 0.6f))
                            .clickable(enabled = active) { settleTo(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else DateroemTextMuted,
                            fontSize = if (index == 0) 10.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Fixed pointer at the top - whichever slot rotates under this is "chosen".
                Text(
                    "▼",
                    color = DateroemAccent,
                    fontSize = 16.sp,
                    modifier = Modifier.offset(y = (-134).dp)
                )
            }

            Text(
                if (settledIndex == 0) "No trip active" else "Trip #$settledIndex selected",
                color = DateroemAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            TextButton(onClick = { closing = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Confirm", color = DateroemAccent)
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DateroemTextMuted)
            }
        }
    }
}

@Composable
private fun WoodDialFace(rotation: Float, slotCount: Int, stepDegrees: Float) {
    Canvas(modifier = Modifier.size(220.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7A4A24), Color(0xFF4A3320), Color(0xFF2E2013)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        var ringRadius = radius * 0.25f
        while (ringRadius < radius) {
            drawCircle(
                color = Color(0xFF3A2716).copy(alpha = 0.35f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )
            ringRadius += radius * 0.18f
        }

        for (index in 0 until slotCount) {
            val angleRad = Math.toRadians((index * stepDegrees + rotation).toDouble())
            val dx = sin(angleRad).toFloat()
            val dy = -cos(angleRad).toFloat()
            drawLine(
                color = Color(0xFF2E2013).copy(alpha = 0.5f),
                start = center,
                end = Offset(center.x + dx * radius, center.y + dy * radius),
                strokeWidth = 1.5f
            )
        }

        drawCircle(color = DateroemAccent.copy(alpha = 0.85f), radius = radius * 0.1f, center = center)
    }
}
