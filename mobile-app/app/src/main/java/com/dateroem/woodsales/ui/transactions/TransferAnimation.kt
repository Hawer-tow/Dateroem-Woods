package com.dateroem.woodsales.ui.transactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel

// The transfer is always forest green (matches the desktop), whatever the app accent is.
private val ForestGreen = Color(0xFF4FA35C)
private val LeafLight = Color(0xFF9BD18B)
private val BarkBrown = Color(0xFF8B6B4A)

private fun DrawScope.drawLeaf(center: Offset, length: Float, angleDeg: Float, color: Color) {
    val path = Path().apply {
        moveTo(-length / 2f, 0f)
        quadraticBezierTo(0f, -length * 0.5f, length / 2f, 0f)
        quadraticBezierTo(0f, length * 0.5f, -length / 2f, 0f)
        close()
    }
    withTransform({
        translate(center.x, center.y)
        rotate(angleDeg, Offset.Zero)
    }) {
        drawPath(path, color)
        // The leaf's spine.
        drawLine(Color.White.copy(alpha = 0.35f), Offset(-length / 2f, 0f), Offset(length / 2f, 0f), strokeWidth = length * 0.06f)
    }
}

// A small pine: two stacked triangles on a trunk.
private fun DrawScope.drawTree(center: Offset, height: Float, color: Color) {
    val w = height * 0.7f
    val top = Path().apply {
        moveTo(center.x, center.y - height / 2f)
        lineTo(center.x + w / 2f, center.y + height * 0.05f)
        lineTo(center.x - w / 2f, center.y + height * 0.05f)
        close()
    }
    val bottom = Path().apply {
        moveTo(center.x, center.y - height * 0.22f)
        lineTo(center.x + w * 0.62f, center.y + height * 0.32f)
        lineTo(center.x - w * 0.62f, center.y + height * 0.32f)
        close()
    }
    drawPath(top, color)
    drawPath(bottom, color)
    drawRect(color, Offset(center.x - height * 0.05f, center.y + height * 0.32f), androidx.compose.ui.geometry.Size(height * 0.1f, height * 0.16f))
}

private fun DrawScope.drawPerson(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius * 0.32f, Offset(center.x, center.y - radius * 0.28f))
    drawArc(
        color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(center.x - radius * 0.55f, center.y + radius * 0.08f),
        size = androidx.compose.ui.geometry.Size(radius * 1.1f, radius * 0.9f)
    )
}

// The screen a payout shows while it is on its way. A payment shouldn't feel
// like a spinner: this shows the company wallet (a tree) handing leaf-coins
// along a track to the recipient, with the stages ticking off underneath.
//   stage 0 - authorising / handing to M-Pesa
//   stage 1 - M-Pesa has it, waiting for its confirmation
@Composable
fun TransferAnimation(amount: String, recipient: String?, stage: Int, waitedSeconds: Int) {
    val transition = rememberInfiniteTransition(label = "transfer")
    val flow by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "flow")
    val pulse by transition.animateFloat(0.9f, 1.18f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val blink by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")

    Column(
        modifier = Modifier.fillMaxWidth().background(DateroemSurface, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconLabel(AppIcons.Lock, "Secure transfer", color = DateroemTextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, iconSize = 13.dp)
        Text("KES $amount", color = ForestGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
            val y = this.size.height / 2f
            val left = Offset(this.size.width * 0.13f, y)
            val right = Offset(this.size.width * 0.87f, y)

            // The moving dashed track.
            drawLine(
                ForestGreen.copy(alpha = 0.35f), left, right, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx()), -flow * 26.dp.toPx())
            )

            // Pulsing halos on both ends.
            drawCircle(ForestGreen.copy(alpha = 0.14f), 34.dp.toPx() * pulse, left)
            drawCircle(ForestGreen.copy(alpha = 0.14f), 34.dp.toPx() * (2.08f - pulse), right)
            drawCircle(ForestGreen, 26.dp.toPx(), left)
            drawCircle(ForestGreen, 26.dp.toPx(), right)
            drawTree(left, 30.dp.toPx(), Color.White)
            drawPerson(right, 26.dp.toPx(), Color.White)

            // Leaf-coins riding the track from wallet to recipient.
            val span = right.x - left.x - 60.dp.toPx()
            for (i in 0 until 4) {
                val p = (flow + i / 4f) % 1f
                val x = left.x + 30.dp.toPx() + span * p
                val lift = -sin(p * PI.toFloat()) * 16.dp.toPx()
                val fade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                drawLeaf(Offset(x, y + lift - 2.dp.toPx()), 20.dp.toPx(), -20f + 40f * p, LeafLight.copy(alpha = 0.35f + 0.65f * fade))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Company wallet", color = DateroemText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(recipient ?: "Recipient", color = DateroemText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            StageRow("Payment authorised", state = if (stage >= 1) StageState.DONE else StageState.ACTIVE, blink = blink)
            StageRow("Sent to M-Pesa", state = if (stage >= 1) StageState.DONE else StageState.WAITING, blink = blink)
            StageRow(
                "Waiting for M-Pesa to confirm" + if (stage >= 1 && waitedSeconds > 0) "  (${waitedSeconds}s)" else "",
                state = if (stage >= 1) StageState.ACTIVE else StageState.WAITING, blink = blink
            )
        }
        Text(
            "Please keep this screen open - the receipt fills in by itself.",
            color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp)
        )
    }
}

private enum class StageState { WAITING, ACTIVE, DONE }

@Composable
private fun StageRow(label: String, state: StageState, blink: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(18.dp).height(18.dp)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            when (state) {
                StageState.WAITING -> drawCircle(DateroemTextMuted.copy(alpha = 0.4f), this.size.width * 0.28f, c, style = Stroke(1.5.dp.toPx()))
                StageState.ACTIVE -> {
                    drawCircle(ForestGreen.copy(alpha = 0.25f * blink), this.size.width * 0.5f, c)
                    drawCircle(ForestGreen.copy(alpha = blink), this.size.width * 0.28f, c)
                }
                StageState.DONE -> {
                    drawCircle(ForestGreen, this.size.width * 0.46f, c)
                    val tick = Path().apply {
                        moveTo(c.x - this@Canvas.size.width * 0.2f, c.y)
                        lineTo(c.x - this@Canvas.size.width * 0.04f, c.y + this@Canvas.size.width * 0.16f)
                        lineTo(c.x + this@Canvas.size.width * 0.22f, c.y - this@Canvas.size.width * 0.14f)
                    }
                    drawPath(tick, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (state == StageState.WAITING) DateroemTextMuted else DateroemText,
            fontWeight = if (state == StageState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

// The moment it lands: a ring blooms, the check draws itself in, and a burst
// of leaves scatters outward.
@Composable
fun SuccessSeal(modifier: Modifier = Modifier) {
    val bloom = remember { Animatable(0f) }
    val scale = remember { Animatable(0.4f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) { bloom.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        check.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(130.dp)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = 40.dp.toPx() * scale.value

        // Expanding ring that fades as it grows.
        drawCircle(ForestGreen.copy(alpha = (1f - bloom.value) * 0.5f), r + 46.dp.toPx() * bloom.value, c, style = Stroke(3.dp.toPx()))

        // Leaves flying out.
        for (i in 0 until 10) {
            val a = (i * 36f + 12f) * (PI.toFloat() / 180f)
            val dist = 44.dp.toPx() + 46.dp.toPx() * bloom.value
            val p = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist)
            drawLeaf(p, 14.dp.toPx(), i * 36f + 12f, LeafLight.copy(alpha = (1f - bloom.value * 0.8f).coerceIn(0f, 1f)))
        }

        drawCircle(ForestGreen, r, c)

        // The check draws itself along its path.
        val full = Path().apply {
            moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
            lineTo(c.x - r * 0.1f, c.y + r * 0.34f)
            lineTo(c.x + r * 0.46f, c.y - r * 0.32f)
        }
        val part = Path()
        val measure = PathMeasure().apply { setPath(full, false) }
        measure.getSegment(0f, measure.length * check.value, part, true)
        drawPath(part, Color.White, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
