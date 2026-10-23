package com.dateroem.woodsales.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.media.ImageCompressor
import com.dateroem.woodsales.data.model.PasswordChangeRequest
import com.dateroem.woodsales.data.model.ProfileUpdateRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.data.session.SessionManager
import com.dateroem.woodsales.ui.auth.AccountState
import com.dateroem.woodsales.ui.common.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

data class ProfileState(
    val loading: Boolean = true,
    val error: String? = null,
    val me: JsonMap? = null,
    val savingDetails: Boolean = false,
    val detailsNote: String? = null,
    val detailsError: String? = null,
    val photoBusy: Boolean = false,
    val photoError: String? = null,
    val passwordBusy: Boolean = false,
    val passwordNote: String? = null,
    val passwordError: String? = null
)

/**
 * "a profile area ... all connected - if one updates on the phone the website
 * and desktop show the same" (23 October 2026, migration 039): the staff
 * member's photo, name, email and password. The phone number receives
 * payouts, so only an admin changes it.
 */
class ProfileViewModel(
    private val api: ApiService,
    private val sessions: SessionManager,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val me = api.me()
                remember(me)
                _state.value = _state.value.copy(loading = false, error = null, me = me)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = describe(e))
            }
        }
    }

    private suspend fun remember(me: JsonMap) {
        val photo = me["photoUrl"] as? String
        AccountState.photoUrl = photo
        sessions.saveAccount(
            name = me["name"] as? String,
            photoUrl = photo,
            idleLockMinutes = (me["idleLockMinutes"] as? Number)?.toInt(),
            mustChangePassword = me["mustChangePassword"] == true
        )
    }

    fun saveDetails(name: String, email: String, password: String) {
        _state.value = _state.value.copy(savingDetails = true, detailsNote = null, detailsError = null)
        viewModelScope.launch {
            try {
                api.updateProfile(ProfileUpdateRequest(name, email.trim().ifBlank { null }, password.ifBlank { null }))
                val me = api.me()
                remember(me)
                _state.value = _state.value.copy(savingDetails = false, me = me, detailsNote = "Saved.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(savingDetails = false, detailsError = describe(e))
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        _state.value = _state.value.copy(photoBusy = true, photoError = null)
        viewModelScope.launch {
            val file = File(appContext.cacheDir, "profile-photo.jpg")
            try {
                ImageCompressor.compressToJpeg(appContext, uri, file, maxDimension = 1024)
                val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                api.uploadProfilePhoto(part)
                val me = api.me()
                remember(me)
                _state.value = _state.value.copy(photoBusy = false, me = me)
            } catch (e: Exception) {
                _state.value = _state.value.copy(photoBusy = false, photoError = describe(e))
            } finally {
                file.delete()
            }
        }
    }

    fun removePhoto() {
        _state.value = _state.value.copy(photoBusy = true, photoError = null)
        viewModelScope.launch {
            try {
                api.removeProfilePhoto()
                val me = api.me()
                remember(me)
                _state.value = _state.value.copy(photoBusy = false, me = me)
            } catch (e: Exception) {
                _state.value = _state.value.copy(photoBusy = false, photoError = describe(e))
            }
        }
    }

    fun changePassword(current: String, new: String, again: String, userName: String?) {
        val problem = passwordProblem(current, new, again, userName, forced = false)
        if (problem != null) {
            _state.value = _state.value.copy(passwordError = problem, passwordNote = null)
            return
        }
        _state.value = _state.value.copy(passwordBusy = true, passwordError = null, passwordNote = null)
        viewModelScope.launch {
            try {
                val token = api.changePassword(PasswordChangeRequest(current, new))["token"] as? String
                    ?: error("No new sign-in came back")
                sessions.passwordChanged(token)
                _state.value = _state.value.copy(
                    passwordBusy = false,
                    passwordNote = "Password changed. Your other devices were signed out."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(passwordBusy = false, passwordError = describe(e))
            }
        }
    }

    class Factory(private val api: ApiService, private val sessions: SessionManager, private val appContext: Context) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(api, sessions, appContext) as T
    }
}

/** The same rule as the server: 8 or more characters, letters and numbers, not the username. */
fun passwordProblem(current: String, new: String, again: String, userName: String?, forced: Boolean): String? = when {
    current.isBlank() -> if (forced) "Enter the temporary password." else "Enter your current password."
    new.length < 8 || new.none { it.isLetter() } || new.none { it.isDigit() } -> "Use at least 8 characters, with letters and numbers."
    userName != null && userName.length >= 3 && new.contains(userName, ignoreCase = true) -> "Don't use your username in your password."
    new != again -> "The two new passwords don't match."
    else -> null
}

/** The server's own sentence when there is one ("detail"), otherwise a calm general one. */
fun describe(e: Exception): String {
    if (e is HttpException) {
        val body = e.response()?.errorBody()?.string().orEmpty()
        Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { return it.groupValues[1].substringAfter("PASSWORD_CHANGE_REQUIRED: ") }
    }
    return friendlyError(e, "Something went wrong. Please try again.")
}

/** 0712 345 678 -> 07•• ••• 678 ("sensitive data show/hide"). */
fun maskPhone(phone: String?): String {
    val digits = phone.orEmpty().filter { it.isDigit() }
    return if (digits.length < 6) (if (phone.isNullOrBlank()) "Not given" else "•••") else "${digits.take(2)}•• ••• ${digits.takeLast(3)}"
}
