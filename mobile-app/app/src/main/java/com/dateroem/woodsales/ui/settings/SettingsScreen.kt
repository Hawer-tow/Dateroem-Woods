package com.dateroem.woodsales.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.AppContainer
import com.dateroem.woodsales.ui.auth.AccountState
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.theme.AppTheme
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemLine
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.DateroemThemeState
import com.dateroem.woodsales.ui.theme.paletteOf
import kotlinx.coroutines.launch

/**
 * Settings, opened from the menu's bottom row (next to Sign out). For now
 * Appearance: "add more themes like the dark and light theme and what we have
 * will be default ... the theme is still the one they choose last" (20
 * October 2026). The choice recolours the app at once - nothing restarts and
 * the fingerprint isn't asked for - and is saved on the phone and with the
 * account, so the website and the desktop app open in it too.
 */
@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val chosen = DateroemThemeState.chosen
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DateroemSurface, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Appearance", color = DateroemText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Saved with your account - the website and the desktop app open in it too.",
                color = DateroemTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeCard(theme = theme, selected = theme == chosen, modifier = Modifier.weight(1f)) {
                        if (theme == chosen) return@ThemeCard
                        DateroemThemeState.chosen = theme
                        scope.launch {
                            container.sessionManager.saveTheme(theme.id, pending = true)
                            note = try {
                                container.api.saveTheme(mapOf("theme" to theme.id))
                                container.sessionManager.saveTheme(theme.id, pending = false)
                                null
                            } catch (_: Exception) {
                                "Saved on this phone. It will reach your account the next time the app is online."
                            }
                        }
                    }
                }
            }
            note?.let {
                Text(it, color = DateroemTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Text(
                "The opening forest and the sign-in screen keep their forest colours.",
                color = DateroemTextMuted.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        // The idle lock (migration 039): the SuperAdmin sets the minutes in the
        // desktop app's Settings; every phone follows them from its next start.
        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(DateroemSurface, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Idle lock", color = DateroemText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Left open and untouched for ${AccountState.idleLockMinutes} minutes, the app locks and asks for your fingerprint. " +
                    "A minute before, it asks if you're still there. The SuperAdmin sets the minutes.",
                color = DateroemTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** A small picture of a theme: its page, a card, two lines of text and the wood. */
@Composable
private fun ThemeCard(theme: AppTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colours = paletteOf(theme)
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) DateroemAccent else DateroemLine.copy(alpha = 0.15f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colours.background)
                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(6.dp)).background(colours.surface))
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(colours.text.copy(alpha = 0.75f)))
                Box(modifier = Modifier.fillMaxWidth(0.5f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(colours.textMuted.copy(alpha = 0.6f)))
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(colours.accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
            if (selected) {
                Icon(AppIcons.Check, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                theme.label,
                color = DateroemText,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
