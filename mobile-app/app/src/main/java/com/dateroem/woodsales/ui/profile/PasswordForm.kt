package com.dateroem.woodsales.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

/**
 * The current (or temporary) password and the new one twice - the profile
 * screen's Password card and the screen after an admin's reset.
 */
@Composable
fun PasswordForm(
    currentLabel: String,
    submitLabel: String,
    busy: Boolean,
    error: String?,
    note: String?,
    onSubmit: (current: String, new: String, again: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val hidden = PasswordVisualTransformation()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password)
    Column {
        OutlinedTextField(current, { current = it }, label = { Text(currentLabel) }, singleLine = true,
            visualTransformation = hidden, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true,
            visualTransformation = hidden, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        OutlinedTextField(again, { again = it }, label = { Text("New password again") }, singleLine = true,
            visualTransformation = hidden, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text(
            "At least 8 characters, with letters and numbers. Your other devices will be signed out.",
            color = DateroemTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        note?.let { Text(it, color = DateroemAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        WoodButton(
            onClick = {
                onSubmit(current, new, again)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
            else Text(submitLabel, fontWeight = FontWeight.Bold)
        }
    }
}
