package com.dateroem.woodsales.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The signed-in account's profile facts the whole app reacts to (migration
 * 039): the photo (menu header), the idle lock's minutes (AppLockGate) and an
 * admin's password reset still waiting for a password of the person's own
 * (AppRoot). Filled from SessionManager.accountFlow - the sign-in answer, then
 * /auth/me when Home opens.
 */
object AccountState {
    // The name as the server last gave it - changed on the website or the
    // desktop, the menu shows the new one as soon as Home has asked.
    var name by mutableStateOf<String?>(null)
    var photoUrl by mutableStateOf<String?>(null)
    var idleLockMinutes by mutableIntStateOf(25)
    var mustChangePassword by mutableStateOf(false)
}

/** When the screen was last touched (MainActivity.onUserInteraction). */
object IdleClock {
    @Volatile
    var lastInteractionAt: Long = System.currentTimeMillis()
        private set

    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
    }
}
