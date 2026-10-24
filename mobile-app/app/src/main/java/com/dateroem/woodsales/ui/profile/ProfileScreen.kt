package com.dateroem.woodsales.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.auth.AccountState
import com.dateroem.woodsales.ui.common.Avatar
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import com.dateroem.woodsales.ui.common.ErrorBox

/**
 * My profile, from the photo or name at the top of the menu ("a profile area
 * like tiktok", 23 October 2026): photo, name, email, phone and password, all
 * saved with the account. The email and phone show partly until the eye is
 * pressed; the phone number is changed by an admin (it receives payouts).
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onOpenWorkerDetails: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val me = state.me

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            return@Box
        }
        if (me == null) {
            Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBox(state.error ?: "Couldn't load your profile")
                TextButton(onClick = viewModel::load) { Text("Try again") }
            }
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PhotoCard(me = me, state = state, viewModel = viewModel)
            DetailsCard(me = me, state = state, viewModel = viewModel)
            // "worker ID number and documents" (migration 040).
            Card(title = "Worker details") {
                Text(
                    "Your ID or passport number, KRA PIN, NSSF and SHA numbers, next of kin and document photos. Only the SuperAdmin can open them.",
                    color = DateroemTextMuted, fontSize = 13.sp
                )
                WoodButton(onClick = onOpenWorkerDetails, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("Open worker details", fontWeight = FontWeight.Bold)
                }
            }
            Card(title = "Password") {
                PasswordForm(
                    currentLabel = "Current password",
                    submitLabel = "Change password",
                    busy = state.passwordBusy,
                    error = state.passwordError,
                    note = state.passwordNote,
                    onSubmit = { current, new, again -> viewModel.changePassword(current, new, again, me["userName"] as? String) }
                )
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DateroemSurface, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = DateroemText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun PhotoCard(me: Map<String, Any?>, state: ProfileState, viewModel: ProfileViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPhoto(uri)
    }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(DateroemSurface, RoundedCornerShape(18.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                name = me["name"] as? String,
                photoUrl = AccountState.photoUrl,
                size = 104.dp,
                modifier = Modifier.clickable(enabled = !state.photoBusy) { choose() }
            )
            if (state.photoBusy) CircularProgressIndicator(color = DateroemAccent, modifier = Modifier.size(36.dp))
        }
        Text(me["name"] as? String ?: "", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("@${me["userName"]}  ·  ${me["roleName"]}", color = DateroemTextMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = choose, enabled = !state.photoBusy) {
                Text(if (AccountState.photoUrl == null) "Add a photo" else "Change photo", color = DateroemAccent)
            }
            if (AccountState.photoUrl != null) {
                TextButton(onClick = viewModel::removePhoto, enabled = !state.photoBusy) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        state.photoError?.let { ErrorBox(it, fontSize = 13.sp) }
    }
}

@Composable
private fun DetailsCard(me: Map<String, Any?>, state: ProfileState, viewModel: ProfileViewModel) {
    var name by remember(me) { mutableStateOf(me["name"] as? String ?: "") }
    var email by remember(me) { mutableStateOf(me["email"] as? String ?: "") }
    var password by remember(me) { mutableStateOf("") }
    var emailShown by remember { mutableStateOf(false) }
    var phoneShown by remember { mutableStateOf(false) }
    val emailChanged = email.trim().lowercase() != (me["email"] as? String ?: "").lowercase()
    val needsPassword = emailChanged && me["hasPassword"] == true

    LaunchedEffect(state.detailsNote) { if (state.detailsNote != null) password = "" }

    Card(title = "Your details") {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it },
            label = { Text("Email") },
            singleLine = true,
            visualTransformation = if (emailShown || emailChanged) VisualTransformation.None else PasswordVisualTransformation('•'),
            trailingIcon = {
                TextButton(onClick = { emailShown = !emailShown }) { Text(if (emailShown) "Hide" else "Show", fontSize = 12.sp) }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        if (needsPassword) {
            OutlinedTextField(
                password, { password = it },
                label = { Text("Your password, to change the email") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        state.detailsError?.let { ErrorBox(it, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp) }
        state.detailsNote?.let { Text(it, color = DateroemAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        WoodButton(
            onClick = { viewModel.saveDetails(name, email, if (needsPassword) password else "") },
            enabled = !state.savingDetails,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (state.savingDetails) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
            else Text("Save details", fontWeight = FontWeight.Bold)
        }

        Text("Phone number", color = DateroemTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val phone = me["phoneNo"] as? String
            Text(if (phoneShown) (phone ?: "Not given") else maskPhone(phone), color = DateroemText, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { phoneShown = !phoneShown }) { Text(if (phoneShown) "Hide" else "Show", fontSize = 12.sp) }
        }
        Text(
            "Your number receives payouts, so an admin changes it for you.",
            color = DateroemTextMuted, fontSize = 12.sp
        )
    }
}
