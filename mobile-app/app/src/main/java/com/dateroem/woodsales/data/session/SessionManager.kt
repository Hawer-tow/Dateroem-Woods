package com.dateroem.woodsales.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dateroem.woodsales.data.model.AuthResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dateroem_session")

data class AccountInfo(val photoUrl: String?, val idleLockMinutes: Int, val mustChangePassword: Boolean)

data class Session(
    val token: String,
    val userId: Int,
    val userName: String,
    val name: String,
    val roleId: Int,
    val roleName: String,
    val canSyncSms: Boolean = false
)

// Persists the signed-in staff member's session across app restarts, and
// is also where the mobile equivalent of the website's "play the splash
// once" logic lives - see SplashViewModel, which re-plays it after the
// app has been away for a while rather than once per install.
class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = intPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val NAME = stringPreferencesKey("name")
        val ROLE_ID = intPreferencesKey("role_id")
        val ROLE_NAME = stringPreferencesKey("role_name")
        val CAN_SYNC_SMS = booleanPreferencesKey("can_sync_sms")
        val LAST_SEEN_AT = stringPreferencesKey("last_seen_at")
        val THEME = stringPreferencesKey("theme")
        val THEME_PENDING = booleanPreferencesKey("theme_pending")
        val MUST_CHANGE_PASSWORD = booleanPreferencesKey("must_change_password")
        val IDLE_LOCK_MINUTES = intPreferencesKey("idle_lock_minutes")
        val PHOTO_URL = stringPreferencesKey("photo_url")
    }

    // Migration 039: the profile photo, the idle lock's minutes (set by the
    // SuperAdmin) and whether an admin's password reset still waits for a
    // password of the person's own. From sign-in and from /auth/me at Home.
    val accountFlow: Flow<AccountInfo> = context.dataStore.data.map { prefs ->
        AccountInfo(
            photoUrl = prefs[Keys.PHOTO_URL],
            idleLockMinutes = prefs[Keys.IDLE_LOCK_MINUTES] ?: 25,
            mustChangePassword = prefs[Keys.MUST_CHANGE_PASSWORD] ?: false
        )
    }

    suspend fun saveAccount(name: String?, photoUrl: String?, idleLockMinutes: Int?, mustChangePassword: Boolean?) {
        context.dataStore.edit { prefs ->
            if (name != null) prefs[Keys.NAME] = name
            if (photoUrl != null) prefs[Keys.PHOTO_URL] = photoUrl else prefs.remove(Keys.PHOTO_URL)
            if (idleLockMinutes != null) prefs[Keys.IDLE_LOCK_MINUTES] = idleLockMinutes
            if (mustChangePassword != null) prefs[Keys.MUST_CHANGE_PASSWORD] = mustChangePassword
        }
    }

    /** After the password changed: this phone's new sign-in (every other one ended). */
    suspend fun passwordChanged(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.MUST_CHANGE_PASSWORD] = false
        }
    }

    // The colour theme: from the account at sign-in, or chosen in Settings
    // (AppTheme ids: "forest", "dark", "light"). Cleared with the session -
    // the next sign-in brings the account's back.
    val themeFlow: Flow<String?> = context.dataStore.data.map { it[Keys.THEME] }

    /** A choice made in Settings that hasn't reached the server yet (offline). */
    suspend fun themePending(): Boolean = context.dataStore.data.map { it[Keys.THEME_PENDING] ?: false }.first()

    suspend fun saveTheme(theme: String, pending: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = theme
            prefs[Keys.THEME_PENDING] = pending
        }
    }

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN] ?: return@map null
        Session(
            token = token,
            userId = prefs[Keys.USER_ID] ?: return@map null,
            userName = prefs[Keys.USER_NAME] ?: "",
            name = prefs[Keys.NAME] ?: "",
            roleId = prefs[Keys.ROLE_ID] ?: 0,
            roleName = prefs[Keys.ROLE_NAME] ?: "",
            canSyncSms = prefs[Keys.CAN_SYNC_SMS] ?: false
        )
    }

    suspend fun tokenOnce(): String? = context.dataStore.data.map { it[Keys.TOKEN] }.first()

    suspend fun save(auth: AuthResponse) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = auth.token
            prefs[Keys.USER_ID] = auth.userId
            prefs[Keys.USER_NAME] = auth.userName
            prefs[Keys.NAME] = auth.name
            prefs[Keys.ROLE_ID] = auth.roleId
            prefs[Keys.ROLE_NAME] = auth.roleName
            prefs[Keys.CAN_SYNC_SMS] = auth.canSyncSms
            auth.theme?.let { prefs[Keys.THEME] = it }
            prefs[Keys.THEME_PENDING] = false
            prefs[Keys.MUST_CHANGE_PASSWORD] = auth.mustChangePassword
            prefs[Keys.IDLE_LOCK_MINUTES] = auth.idleLockMinutes
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // Why the app was signed out by the server (the phone was revoked, the
    // sign-in is too old, ...) - shown once on the sign-in screen.
    private val _signOutReason = MutableStateFlow<String?>(null)
    val signOutReason: StateFlow<String?> = _signOutReason.asStateFlow()

    suspend fun endSession(reason: String) {
        _signOutReason.value = reason
        clear()
    }

    fun clearSignOutReason() {
        _signOutReason.value = null
    }

    // Coming back to the app from the background, the entrance plays again
    // only after this long away (business rules, mobile: unlike the
    // website's once-per-session version), so a quick look at another app
    // doesn't bring the whole entrance back. Opened afresh - after it was
    // closed (swiped away) or Android cleared it - it always plays ("like
    // other apps like netflix", 19 October 2026; see AppRoot).
    suspend fun shouldShowSplash(): Boolean {
        val lastSeen = context.dataStore.data.map { it[Keys.LAST_SEEN_AT] }.first()?.toLongOrNull()
        val awayMs = System.currentTimeMillis() - (lastSeen ?: 0L)
        return awayMs > SPLASH_REPLAY_THRESHOLD_MS
    }

    suspend fun markSeenNow() {
        context.dataStore.edit { it[Keys.LAST_SEEN_AT] = System.currentTimeMillis().toString() }
    }

    companion object {
        // 3 minutes (was 15 until 19 October 2026: "waiting for 15 minutes is too long").
        private const val SPLASH_REPLAY_THRESHOLD_MS = 3 * 60 * 1000L
    }
}
