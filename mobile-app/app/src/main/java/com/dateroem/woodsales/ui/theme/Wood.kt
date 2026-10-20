package com.dateroem.woodsales.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.R

// "since this wood for areas previously having the orange color looks good
// you can extend it to the mobile and desktop" - the website's painted wood,
// carried into the app. The images in res/drawable-nodpi are rendered from
// the website's own painter (frontend/website/src/wood, `npm run
// wood:export`), so the site, this app and the desktop app all wear the same
// wood: a sawn plank for filled buttons, a freshly cut log end for the round
// buttons, and the tree-ring Dateroem wordmark.

// The painted board, tiled at its natural size so the grain is the same
// scale on every button.
fun Modifier.woodPlank(shape: Shape): Modifier = composed {
    val plank = ImageBitmap.imageResource(R.drawable.wood_plank)
    val brush = remember(plank) { ShaderBrush(ImageShader(plank, TileMode.Repeated, TileMode.Repeated)) }
    // A faint bark edge so it reads as a cut piece of wood, not a flat swatch.
    clip(shape).background(brush, shape).border(1.dp, WoodBark.copy(alpha = 0.4f), shape)
}

// The app's main action button, painted as a board with dark bark-brown
// lettering. Same shape and padding as Material's Button, which it wraps.
@Composable
fun WoodButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.55f).woodPlank(shape),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = WoodOn,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = WoodOn
        ),
        contentPadding = contentPadding,
        content = content
    )
}

// The round "add" button on list screens: a freshly cut log end (pale heart,
// rings, bark rim), like the website's help button.
@Composable
fun WoodFab(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        WoodLogEnd(Modifier.matchParentSize())
        CompositionLocalProvider(LocalContentColor provides WoodOn) { content() }
    }
}

@Composable
fun WoodLogEnd(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.wood_log_end),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape)
    )
}

// The tree-ring "Dateroem" wordmark from the website's navbar.
@Composable
fun DateroemWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.dateroem_wordmark),
        contentDescription = "Dateroem",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
