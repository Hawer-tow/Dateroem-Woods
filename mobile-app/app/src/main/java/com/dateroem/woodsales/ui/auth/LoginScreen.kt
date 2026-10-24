package com.dateroem.woodsales.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.dateroem.woodsales.R
import com.dateroem.woodsales.ui.common.animationsOff
import com.dateroem.woodsales.ui.common.ENTRANCE_END_MS
import com.dateroem.woodsales.ui.common.drawEntranceScrim
import com.dateroem.woodsales.ui.common.drawGreenWash
import com.dateroem.woodsales.ui.common.drawPhotoCover
import com.dateroem.woodsales.ui.common.drawRestingMist
import com.dateroem.woodsales.ui.common.rememberEntrancePhoto
import com.dateroem.woodsales.ui.common.rememberFrameClock
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemWordmark
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
    // Why the server ended the last session (phone revoked, ...), if it did.
    signOutReason: String? = null
) {
    var userName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoggedIn()
    }

    // The entrance's last picture without the brand (ui/common/Entrance.kt):
    // the same misty forest, its mist carrying on from where the entrance
    // left it, the same shade and the app's green wash - so the entrance
    // dissolves into sign-in without a jump (AppRoot, 19 October 2026).
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val forest = rememberEntrancePhoto(R.drawable.entrance_forest)
    val fog = rememberEntrancePhoto(R.drawable.entrance_fog)
    val clock = rememberFrameClock(running = !still)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07080A))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (forest != null) {
                drawPhotoCover(forest, zoom = 1.03f, pivotY = 0.45f)
            } else {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0F241B), Color(0xFF1D4232), Color(0xFF295A45))))
            }
            if (fog != null) {
                drawRestingMist(fog, ENTRANCE_END_MS + clock)
            }
            drawEntranceScrim(0.62f)
            drawGreenWash(1f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The tree-ring wordmark from the website's navbar, in place of
            // plain "DATEROEM" lettering.
            DateroemWordmark(modifier = Modifier.fillMaxWidth(0.8f))
            Text(
                text = "Wood Sales - Staff Sign In",
                color = DateroemTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DateroemSurface.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DateroemAccent,
                    unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f),
                    focusedLabelColor = DateroemAccent,
                    unfocusedLabelColor = DateroemTextMuted,
                    focusedTextColor = DateroemText,
                    unfocusedTextColor = DateroemText,
                    cursorColor = DateroemAccent
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                if (signOutReason != null && state !is LoginUiState.AwaitingApproval) {
                    NoticeRow(icon = AppIcons.Warning, text = signOutReason)
                }

                (state as? LoginUiState.AwaitingApproval)?.let { waiting ->
                    AwaitingApprovalCard(message = waiting.message, phoneName = waiting.phoneName)
                }

                if (state is LoginUiState.Error) {
                    ErrorBox((state as LoginUiState.Error).message, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
                }

                WoodButton(
                    onClick = { viewModel.login(userName, password) },
                    enabled = state !is LoginUiState.Loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (state is LoginUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), color = WoodOn)
                    } else {
                        Icon(AppIcons.Tree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state is LoginUiState.AwaitingApproval) "Check again" else "Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// A short notice above the form - "why am I signed out?".
@Composable
private fun NoticeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(DateroemAccent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = DateroemText, fontSize = 13.sp)
    }
}

// "allows only one user per account per mobile phone" - this phone is new
// for the account, so an admin has to approve it before it can sign in.
@Composable
private fun AwaitingApprovalCard(message: String, phoneName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(DateroemAccent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Smartphone, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Waiting for approval", color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(text = message, color = DateroemText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text(text = "This phone: $phoneName", color = DateroemTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}