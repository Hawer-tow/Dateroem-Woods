package com.dateroem.woodsales.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.WoodLogEnd
import androidx.compose.material3.Icon
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.theme.WoodOn

// The chat assistant, no longer a drawer section: a small glowing orb at the
// bottom-right, level with the trip selector in the bottom middle. A slow
// breathing halo behind it says "ask me" without shouting; the orb itself is
// a freshly cut log end, same as the website's help button.
@Composable
fun ChatFloatingButton(onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 48.dp) {
    val infinite = rememberInfiniteTransition(label = "chat-orb")
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .drawBehind {
                // Soft halo that swells and fades behind the orb.
                val radius = size.minDimension / 2f * (1.05f + 0.38f * breathe)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(DateroemAccent.copy(alpha = 0.34f * (1f - 0.55f * breathe)), Color.Transparent),
                        radius = radius
                    ),
                    radius = radius
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(diameter * 0.8f)
                .shadow(5.dp, CircleShape, ambientColor = DateroemAccent, spotColor = DateroemAccent)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            WoodLogEnd(Modifier.matchParentSize())
            Icon(AppIcons.Sparkles, contentDescription = "Assistant", tint = WoodOn, modifier = Modifier.size(diameter * 0.34f))
        }
    }
}
