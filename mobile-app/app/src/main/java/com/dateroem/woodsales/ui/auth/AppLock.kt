package com.dateroem.woodsales.ui.auth

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.util.UUID
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemThemeState
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.DateroemWordmark
import com.dateroem.woodsales.ui.theme.WoodButton

// "add the additional fingerprint unlock" - the signed-in app opens only for
// the phone's owner: fingerprint when the phone has one set up, otherwise
// the phone's own PIN, pattern or password. Even someone holding an approved
// phone can't get into the app without it.
private const val LOCK_AFTER_AWAY_MS = 2 * 60 * 1000L

// Different every time the app's process starts.
private val PROCESS_RUN = UUID.randomUUID().toString()

/**
 * Saves whether the app is locked together with the run that saved it. A
 * screen brought back from its saved state in a new run - Android closed the
 * app while it was in the background - comes back locked, whatever was saved.
 */
private val LockedSaver: Saver<MutableState<Boolean>, Any> = listSaver(
    save = { listOf(PROCESS_RUN, it.value) },
    restore = { saved -> mutableStateOf(saved[0] != PROCESS_RUN || saved[1] as Boolean) }
)

/** True while the entrance plays over the app (AppRoot); the fingerprint prompt waits for it. */
val LocalEntrancePlaying = compositionLocalOf { false }

private enum class LockMethod { BIOMETRIC, BIOMETRIC_OR_SCREEN_LOCK, NONE }

private fun lockMethod(context: Context): LockMethod {
    val manager = BiometricManager.from(context)
    return when {
        manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> LockMethod.BIOMETRIC
        manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS -> LockMethod.BIOMETRIC_OR_SCREEN_LOCK
        else -> LockMethod.NONE
    }
}

/**
 * Shows [content] only while unlocked. Locks when the app is opened
 * ([startLocked]) and again whenever it comes back after being away for
 * more than two minutes (a quick trip to the camera doesn't count).
 *
 * The screens stay where they were underneath the lock: it used to replace
 * them, so coming back after recording a long video started over at Home
 * and a half-filled form lost what was typed and picked ("the video is
 * gone"). The lock covers them, hides them from screen readers and takes
 * every touch until it's opened.
 */
@Composable
fun AppLockGate(startLocked: Boolean, onSignOut: () -> Unit, content: @Composable () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    // Kept with the screen's saved state, so a screen Android rebuilds while
    // the app runs (the language or text size changed; turning the phone no
    // longer rebuilds it at all) stays unlocked - "rotating a screen is not the
    // same as closing the phone" (20 October 2026). An app opened afresh has no
    // saved state and starts locked, and so does one Android closed while it
    // was away and brings back (LockedSaver).
    var locked by rememberSaveable(saver = LockedSaver) { mutableStateOf(startLocked) }
    var leftAt by rememberSaveable { mutableLongStateOf(0L) }
    val focusManager = LocalFocusManager.current

    // Nothing can be typed into the covered screen.
    LaunchedEffect(locked) {
        if (locked) focusManager.clearFocus(force = true)
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // A screen being rebuilt also stops for a moment; that is not leaving.
                Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) leftAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (leftAt > 0 && System.currentTimeMillis() - leftAt > LOCK_AFTER_AWAY_MS) locked = true
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
            content()
        }
        if (locked) {
            // Back leaves the app rather than moving the covered screens.
            BackHandler { activity.moveTaskToBack(true) }
            LockScreen(activity = activity, onUnlocked = { locked = false }, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val method = remember { lockMethod(activity) }
    var message by remember { mutableStateOf<String?>(null) }
    var prompts by remember { mutableStateOf(0) }

    fun prompt() {
        val authenticators = if (method == LockMethod.BIOMETRIC) BIOMETRIC_STRONG else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Dateroem")
            .setSubtitle(if (method == LockMethod.BIOMETRIC) "Use your fingerprint" else "Use your fingerprint or screen lock")
            .setAllowedAuthenticators(authenticators)
            .apply { if (method == LockMethod.BIOMETRIC) setNegativeButtonText("Cancel") }
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                message = null
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                message = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    null
                } else {
                    errString.toString()
                }
            }
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    // Ask straight away the first time the lock screen shows - or, when the
    // entrance is playing over it (coming back after a while), as soon as
    // the entrance ends, so the system prompt doesn't cover it.
    val entrancePlaying = LocalEntrancePlaying.current
    LaunchedEffect(entrancePlaying) {
        if (!entrancePlaying && method != LockMethod.NONE && prompts == 0) {
            prompts++
            prompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(DateroemThemeState.palette.lockGradient))
            // Registers every touch on the lock itself, so none reach the
            // covered screen (its own buttons still work as normal).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DateroemWordmark(modifier = Modifier.fillMaxWidth(0.7f))
            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .size(96.dp)
                    .background(DateroemAccent.copy(alpha = 0.14f), RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Fingerprint, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(52.dp))
            }
            if (method == LockMethod.NONE) {
                Text(
                    "Set a screen lock first",
                    color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    "This phone has no fingerprint, PIN or pattern. Add one in the phone's Settings to keep the company's data safe, then come back.",
                    color = DateroemTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                WoodButton(
                    onClick = { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) { Text("Open security settings", fontWeight = FontWeight.Bold) }
            } else {
                Text(
                    "Dateroem is locked",
                    color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    if (method == LockMethod.BIOMETRIC) "Touch the fingerprint sensor to continue." else "Use your fingerprint or screen lock to continue.",
                    color = DateroemTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                message?.let {
                    Text(it, color = DateroemAccent, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
                WoodButton(
                    onClick = { prompt() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    Icon(AppIcons.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Unlock", fontWeight = FontWeight.Bold)
                }
            }
            // Asks first, like the menu's Sign out (SignOutConfirmDialog).
            var confirmSignOut by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Sign out", color = DateroemTextMuted)
            }
            if (confirmSignOut) {
                SignOutConfirmDialog(
                    onConfirm = {
                        confirmSignOut = false
                        onSignOut()
                    },
                    onDismiss = { confirmSignOut = false }
                )
            }
        }
    }
}
