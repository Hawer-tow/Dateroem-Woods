package com.dateroem.woodsales.ui.transactions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.session.CompanySimStore
import com.dateroem.woodsales.data.sms.SmsQueueSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

data class SimOption(val subscriptionId: Int, val label: String, val slotIndex: Int)

data class SmsSyncUiState(
    val hasPermissions: Boolean = false,
    val availableSims: List<SimOption> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val selectedLabel: String? = null,
    val resyncing: Boolean = false,
    val lastResult: SmsQueueSync.ResyncResult? = null,
    val error: String? = null
)

// "so what if it had 2 sims it will read the company sim" - the company
// SIM is picked once here and remembered (CompanySimStore); SmsReceiver
// and the resync scan below both check against it before touching a
// message. Everyone else on this phone's other SIM (if any) is never read.
class SmsSyncViewModel(private val context: Context, private val companySimStore: CompanySimStore) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsSyncUiState())
    val uiState: StateFlow<SmsSyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val subscriptionId = companySimStore.subscriptionIdOnce()
            _uiState.value = _uiState.value.copy(selectedSubscriptionId = subscriptionId)
        }
        viewModelScope.launch {
            companySimStore.label.collect { label -> _uiState.value = _uiState.value.copy(selectedLabel = label) }
        }
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        val granted = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
        if (granted) loadSims()
    }

    private fun loadSims() {
        val sims = try {
            @Suppress("MissingPermission")
            val manager = SubscriptionManager.from(context)
            (manager.activeSubscriptionInfoList ?: emptyList()).map { info ->
                // The line number is often withheld by the carrier/OS even
                // with permission granted - falls back to carrier name +
                // slot so there's always something to tell the two apart by.
                val number = try { info.number?.takeIf { it.isNotBlank() } } catch (e: SecurityException) { null }
                val label = listOfNotNull(info.carrierName?.toString(), number).joinToString(" - ")
                    .ifBlank { "SIM slot ${info.simSlotIndex + 1}" }
                SimOption(info.subscriptionId, label, info.simSlotIndex)
            }
        } catch (e: SecurityException) {
            emptyList()
        }
        _uiState.value = _uiState.value.copy(availableSims = sims, error = null)
    }

    fun selectSim(option: SimOption) {
        viewModelScope.launch {
            companySimStore.setCompanySim(option.subscriptionId, option.label)
            _uiState.value = _uiState.value.copy(selectedSubscriptionId = option.subscriptionId, selectedLabel = option.label)
        }
    }

    // "check the messages compare at times" - a manual catch-up over the
    // last 30 days, for messages that arrived while offline or before this
    // was set up. Re-running it is always safe (server dedupes by receipt).
    fun resync() {
        val state = _uiState.value
        if (state.selectedSubscriptionId == null || state.resyncing) return
        _uiState.value = state.copy(resyncing = true, error = null)
        viewModelScope.launch {
            try {
                val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val result = SmsQueueSync.resyncInbox(context, since)
                _uiState.value = _uiState.value.copy(resyncing = false, lastResult = result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(resyncing = false, error = friendlyError(e, "Couldn't scan the inbox"))
            }
        }
    }

    class Factory(private val context: Context, private val companySimStore: CompanySimStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SmsSyncViewModel(context.applicationContext, companySimStore) as T
    }
}
