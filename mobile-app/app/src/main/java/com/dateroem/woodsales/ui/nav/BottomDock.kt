package com.dateroem.woodsales.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.ai.ChatFloatingButton
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.trips.TripDialTriggerButton
import androidx.compose.material3.Icon
import com.dateroem.woodsales.ui.common.AppIcons

// One small rounded dock at the bottom-centre instead of separate floating
// buttons: the main-menu shortcut on the left (no reaching for the top-left
// corner), the trip selector in the middle - the largest, since it's the
// control that's used the most - and the AI assistant on the right. The two
// side buttons are a touch smaller than the selector so it stays the anchor.
@Composable
fun BottomDock(
    activeSlotLabel: String,
    onMenu: () -> Unit,
    onTrip: () -> Unit,
    onChat: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(DateroemSurface.copy(alpha = 0.72f))
            .border(1.dp, DateroemAccent.copy(alpha = 0.35f), RoundedCornerShape(36.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Menu, contentDescription = "Menu", tint = DateroemText, modifier = Modifier.size(22.dp))
        }
        TripDialTriggerButton(activeSlotLabel = activeSlotLabel, onClick = onTrip)
        if (onChat != null) {
            ChatFloatingButton(onClick = onChat, diameter = 44.dp)
        } else {
            // Keep the trip selector centred when there's no assistant for this role.
            Box(modifier = Modifier.size(38.dp))
        }
    }
}
