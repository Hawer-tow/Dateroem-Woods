package com.dateroem.woodsales.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemText
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel

// The chat assistant as a compact floating card - like the website's help
// panel: inset from the top and the bottom of the screen with rounded corners
// over a dimmed page, not a full-screen takeover. Tapping outside the card
// (or the X) closes it; the conversation is kept, so reopening picks up where
// it left off. The card shrinks with the keyboard instead of being pushed off.
@Composable
fun ChatPanel(viewModel: ChatViewModel, onClose: () -> Unit, onNavigate: (String) -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                // Status bar, nav bar and the keyboard: everything inside is
                // laid out in what's left, so the card never hides behind them.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 14.dp, end = 14.dp, top = 40.dp, bottom = 40.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DateroemBackground)
                    .border(1.dp, DateroemAccent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    // Swallow taps on the card itself so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconLabel(AppIcons.Sparkles, "Assistant", color = DateroemText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, iconSize = 18.dp, iconTint = DateroemAccent, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Close", tint = DateroemText, modifier = Modifier.size(20.dp)) }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ChatScreen(viewModel, onNavigate = onNavigate)
                }
            }
        }
    }
}
