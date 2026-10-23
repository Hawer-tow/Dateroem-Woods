package com.dateroem.woodsales.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dateroem.woodsales.AppContainer
import com.dateroem.woodsales.data.session.Session
import com.dateroem.woodsales.ui.auth.AccountState
import com.dateroem.woodsales.ui.auth.AppLockGate
import com.dateroem.woodsales.ui.profile.ForcedPasswordScreen
import com.dateroem.woodsales.ui.auth.AuthViewModel
import com.dateroem.woodsales.ui.auth.LocalEntrancePlaying
import com.dateroem.woodsales.ui.auth.LoginScreen
import com.dateroem.woodsales.ui.splash.SplashScreen
import com.dateroem.woodsales.ui.theme.AppTheme
import com.dateroem.woodsales.ui.theme.DateroemThemeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class RootPhase { LOADING, SPLASH, LOGIN, HOME }

/**
 * Brings the phone's theme and the account's together: a choice made in
 * Settings while offline is sent now; otherwise the account's (perhaps
 * changed on the website or the desktop) is taken. Without a connection
 * the phone's copy simply stays.
 */
private suspend fun syncTheme(container: AppContainer) {
    val sessions = container.sessionManager
    try {
        val me = container.api.me()
        // The profile (migration 039): the photo, the idle lock's minutes and
        // whether an admin's reset still waits for a new password.
        sessions.saveAccount(
            name = me["name"] as? String,
            photoUrl = me["photoUrl"] as? String,
            idleLockMinutes = (me["idleLockMinutes"] as? Number)?.toInt(),
            mustChangePassword = me["mustChangePassword"] == true
        )
        if (sessions.themePending()) {
            val mine = DateroemThemeState.chosen.id
            container.api.saveTheme(mapOf("theme" to mine))
            sessions.saveTheme(mine, pending = false)
        } else {
            val account = me["theme"] as? String ?: return
            sessions.saveTheme(AppTheme.fromId(account).id, pending = false)
        }
    } catch (_: Exception) {
        // Offline or signed out meanwhile - try again next time.
    }
}

/** How long the entrance takes to dissolve into sign-in. */
private const val ENTRANCE_DISSOLVE_MS = 750

@Composable
fun AppRoot(container: AppContainer) {
    var phase by remember { mutableStateOf(RootPhase.LOADING) }
    var session by remember { mutableStateOf<Session?>(null) }
    // Someone who has just typed their password isn't asked for their
    // fingerprint straight after; opening the app with a saved sign-in is.
    var justSignedIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Whether this screen has played the entrance yet. Kept with the screen's
    // saved state: it survives the screen being rebuilt (the phone turned,
    // or Android bringing back an app it had put away) but not the app being
    // closed, so an app opened afresh always plays the entrance.
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val existing = container.sessionManager.sessionFlow.first()
        // "like other apps like netflix ... open it close it open close it ...
        // one can see the entrance" (19 October 2026): opened afresh, the
        // entrance always plays; a rebuilt screen plays it only after the
        // app has been away a while (SessionManager.shouldShowSplash).
        val showSplash = !entrancePlayed || container.sessionManager.shouldShowSplash()
        entrancePlayed = true
        session = existing
        phase = if (showSplash) RootPhase.SPLASH else if (existing != null) RootPhase.HOME else RootPhase.LOGIN
    }

    // "the entry images for the app play once instead play in case the app
    // was closed" (18 October 2026). Back or Home only sends the app to the
    // background - Android keeps it - so coming back never started it afresh
    // and the entrance never replayed. Now leaving the app records the time,
    // and coming back after more than 3 minutes (SessionManager) plays the
    // entrance over the screen the person was on, which stays as it was
    // underneath (behind the fingerprint lock when that is due; its prompt
    // waits for the entrance to end).
    var replaying by remember { mutableStateOf(false) }
    val activity = LocalContext.current as FragmentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> scope.launch { container.sessionManager.markSeenNow() }
                Lifecycle.Event.ON_START -> scope.launch {
                    if ((phase == RootPhase.HOME || phase == RootPhase.LOGIN) && container.sessionManager.shouldShowSplash()) {
                        replaying = true
                    }
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // The colour theme (Settings; ui/theme/Color.kt): the phone's copy of the
    // account's choice, applied as soon as it is read. The entrance and
    // sign-in keep the forest colours of their photographs; the chosen theme
    // takes over once signed in. Changing it only recolours - nothing is
    // rebuilt, so the fingerprint lock is never asked for again.
    LaunchedEffect(Unit) {
        container.sessionManager.themeFlow.collect { DateroemThemeState.chosen = AppTheme.fromId(it) }
    }
    // The photo, the idle lock's minutes and a waiting password change (migration 039).
    LaunchedEffect(Unit) {
        container.sessionManager.accountFlow.collect { account ->
            AccountState.photoUrl = account.photoUrl
            AccountState.idleLockMinutes = account.idleLockMinutes
            AccountState.mustChangePassword = account.mustChangePassword
        }
    }
    // Read here, in AppRoot's own body, so this runs again whenever the phase
    // changes: read only inside the content below, a change of phase redrew
    // just that content and the app stayed in the forest colours after
    // signing in (20 October 2026).
    val forestOnly = phase != RootPhase.HOME
    SideEffect { DateroemThemeState.forestOnly = forestOnly }

    CompositionLocalProvider(LocalEntrancePlaying provides replaying) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The entrance dissolves into sign-in ("let it feel like a
            // transition graceful", 19 October 2026): sign-in - whose backdrop
            // is the entrance's last picture without the brand - fades in over
            // the entrance, which stays as it is underneath until covered, so
            // the shared forest never dims. Every other change is immediate.
            AnimatedContent(
                targetState = phase,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (initialState == RootPhase.SPLASH && targetState == RootPhase.LOGIN) {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(ENTRANCE_DISSOLVE_MS)),
                            initialContentExit = fadeOut(tween(durationMillis = 1, delayMillis = ENTRANCE_DISSOLVE_MS)),
                            targetContentZIndex = 1f,
                            sizeTransform = null
                        )
                    } else {
                        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                    }
                },
                label = "root"
            ) { shown ->
                when (shown) {
                    RootPhase.LOADING -> Unit
                    RootPhase.SPLASH -> {
                        SplashScreen(onFinish = {
                            phase = if (session != null) RootPhase.HOME else RootPhase.LOGIN
                        })
                    }
                    RootPhase.LOGIN -> {
                        val viewModel = remember {
                            AuthViewModel.Factory(container.api, container.sessionManager, container.deviceIdentity)
                                .create(AuthViewModel::class.java)
                        }
                        val signOutReason by container.sessionManager.signOutReason.collectAsState()
                        LoginScreen(viewModel = viewModel, signOutReason = signOutReason, onLoggedIn = {
                            justSignedIn = true
                            phase = RootPhase.HOME
                        })
                    }
                    RootPhase.HOME -> {
                        LaunchedEffect(Unit) {
                            container.sessionManager.markSeenNow()
                            session = container.sessionManager.sessionFlow.first()
                        }
                        // The account's theme may have been changed on the website
                        // or the desktop since; a choice made here offline goes up.
                        LaunchedEffect(Unit) { syncTheme(container) }
                        // "only one user per account per mobile phone" - the server can
                        // end this phone's session at any time (an admin revoked or
                        // replaced the phone, the account was disabled); the app then
                        // goes straight back to sign-in with the reason.
                        LaunchedEffect(Unit) {
                            container.sessionManager.sessionFlow.collect { current ->
                                if (current == null) {
                                    session = null
                                    justSignedIn = false
                                    phase = RootPhase.LOGIN
                                }
                            }
                        }
                        val current = session
                        if (current != null) {
                            val signOut: () -> Unit = {
                                scope.launch {
                                    container.sessionManager.clear()
                                    session = null
                                    justSignedIn = false
                                    phase = RootPhase.LOGIN
                                }
                            }
                            if (AccountState.mustChangePassword) {
                                // An admin reset the password: a new one of the
                                // person's own comes before anything else.
                                ForcedPasswordScreen(container = container, userName = current.userName, onSignOut = signOut)
                            } else {
                                AppLockGate(startLocked = !justSignedIn, onSignOut = signOut) {
                                    HomeShell(container = container, session = current, onLoggedOut = {
                                        session = null
                                        justSignedIn = false
                                        phase = RootPhase.LOGIN
                                    })
                                }
                            }
                        }
                    }
                }
            }
            // The entrance again, over everything, when coming back after a while.
            if (replaying) {
                SplashScreen(onFinish = { replaying = false })
            }
        }
    }
}
