package com.dateroem.woodsales.ui.deliveries

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import java.io.ByteArrayOutputStream

// Fixed raster size the signature is rasterized into on save, independent
// of whatever size the pad happens to render at on screen - a phone in
// landscape vs portrait shouldn't produce differently-shaped signatures.
private const val RASTER_WIDTH = 600
private const val RASTER_HEIGHT = 220

// "add a signature area which is stored in the signature blob" - captures
// finger strokes as the person actually draws them (not a typed name), and
// rasterizes those exact strokes into a small PNG data URL on save, the
// same shape as every other image already stored in this schema (Product
// photos, production-description photos).
class SignatureCapture {
    private val strokes = mutableListOf<MutableList<Offset>>()
    private var padWidth = 0f
    private var padHeight = 0f
    var hasSignature by mutableStateOf(false)
        private set

    // strokes is a plain MutableList, which Compose can't see change - so
    // nothing redrew while actually dragging a finger across the pad, only
    // signing appeared to do nothing. This is bumped on every point so the
    // Canvas draw phase (which reads it) knows to redraw each time.
    var revision by mutableStateOf(0)
        private set

    fun setPadSize(width: Float, height: Float) {
        padWidth = width
        padHeight = height
    }

    fun startStroke(point: Offset) {
        strokes.add(mutableListOf(point))
        hasSignature = true
        revision++
    }

    fun extendStroke(point: Offset) {
        strokes.lastOrNull()?.add(point)
        revision++
    }

    fun clear() {
        strokes.clear()
        hasSignature = false
        revision++
    }

    fun strokesSnapshot(): List<List<Offset>> = strokes.map { it.toList() }

    // Replays the captured strokes onto a fixed-size off-screen bitmap,
    // scaled from the on-screen pad size, and returns a PNG data URL -
    // null if nothing was drawn (a blank signature isn't worth storing).
    fun toDataUrl(): String? {
        if (strokes.isEmpty() || padWidth <= 0f || padHeight <= 0f) return null
        val scaleX = RASTER_WIDTH / padWidth
        val scaleY = RASTER_HEIGHT / padHeight

        val imageBitmap = ImageBitmap(RASTER_WIDTH, RASTER_HEIGHT)
        val canvas = Canvas(imageBitmap)
        canvas.drawRect(
            androidx.compose.ui.geometry.Rect(0f, 0f, RASTER_WIDTH.toFloat(), RASTER_HEIGHT.toFloat()),
            Paint().apply { color = Color.White; style = PaintingStyle.Fill }
        )
        val paint = Paint().apply {
            color = Color.Black
            strokeWidth = 5f
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
        }
        for (stroke in strokes) {
            if (stroke.size < 2) continue
            val path = Path()
            path.moveTo(stroke[0].x * scaleX, stroke[0].y * scaleY)
            for (i in 1 until stroke.size) {
                path.lineTo(stroke[i].x * scaleX, stroke[i].y * scaleY)
            }
            canvas.drawPath(path, paint)
        }

        val bitmap: Bitmap = imageBitmap.asAndroidBitmap()
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }
}

@Composable
fun rememberSignatureCapture(): SignatureCapture = remember { SignatureCapture() }

@Composable
fun SignaturePad(capture: SignatureCapture, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, DateroemTextMuted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> capture.startStroke(offset) },
                        onDrag = { change, _ -> capture.extendStroke(change.position) }
                    )
                }
        ) {
            capture.setPadSize(this.size.width, this.size.height)
            // Reading revision here is what makes this draw phase re-run
            // as the strokes list grows during a drag.
            @Suppress("UNUSED_EXPRESSION")
            capture.revision
            capture.strokesSnapshot().forEach { stroke ->
                if (stroke.size < 2) return@forEach
                val path = Path()
                path.moveTo(stroke[0].x, stroke[0].y)
                for (i in 1 until stroke.size) path.lineTo(stroke[i].x, stroke[i].y)
                drawPath(path, color = Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (capture.hasSignature) "Signed" else "Sign above with your finger",
                color = if (capture.hasSignature) DateroemAccent else DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { capture.clear() }) { Text("Clear") }
        }
    }
}
