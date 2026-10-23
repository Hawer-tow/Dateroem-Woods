package com.dateroem.woodsales.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.AppContainer
import com.dateroem.woodsales.data.model.PasswordChangeRequest
import com.dateroem.woodsales.ui.auth.AccountState
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.DateroemWordmark
import kotlinx.coroutines.launch

/**
 * After an admin resets the password (migration 039) the temporary one works
 * for one thing only - choosing a password of the person's own - so this
 * screen comes before anything else. It can only be left by signing out.
 */
@Composable
fun ForcedPasswordScreen(container: AppContainer, userName: String?, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            DateroemWordmark(modifier = Modifier.padding(top = 24.dp, bottom = 20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DateroemSurface, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text("Choose a new password", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Your password was reset by an admin. Enter the temporary password you were given, then a new one of your own.",
                    color = DateroemTextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                PasswordForm(
                    currentLabel = "Temporary password",
                    submitLabel = "Save my new password",
                    busy = busy,
                    error = error,
                    note = null,
                    onSubmit = { current, new, again ->
                        val problem = passwordProblem(current, new, again, userName, forced = true)
                        if (problem != null) {
                            error = problem
                        } else {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val token = container.api.changePassword(PasswordChangeRequest(current, new))["token"] as? String
                                        ?: error("No new sign-in came back")
                                    container.sessionManager.passwordChanged(token)
                                    AccountState.mustChangePassword = false
                                } catch (e: Exception) {
                                    error = describe(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
            }
            TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 12.dp)) { Text("Sign out") }
        }
    }
}
