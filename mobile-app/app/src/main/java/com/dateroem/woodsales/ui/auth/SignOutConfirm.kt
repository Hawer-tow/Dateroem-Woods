package com.dateroem.woodsales.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.dateroem.woodsales.ui.theme.DateroemDanger
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

/**
 * "one in mobile might click the sign out accidentally ... can this be
 * prevented by having a selection are you sure you want to sign out" (20
 * October 2026) - every Sign out on the phone (the menu, the fingerprint
 * lock) asks first. Cancel is the safe default; tapping outside also cancels.
 */
@Composable
fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DateroemSurface,
        titleContentColor = DateroemText,
        textContentColor = DateroemTextMuted,
        title = { Text("Sign out?", fontWeight = FontWeight.Bold) },
        text = { Text("You will need your username and password to sign in again on this phone.") },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DateroemText) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out", color = DateroemDanger, fontWeight = FontWeight.Bold) }
        }
    )
}
