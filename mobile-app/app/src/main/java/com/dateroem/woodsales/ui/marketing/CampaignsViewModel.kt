package com.dateroem.woodsales.ui.marketing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface CampaignsUiState {
    data object Loading : CampaignsUiState
    data class Loaded(val campaigns: List<Campaign>) : CampaignsUiState
    data class Error(val message: String) : CampaignsUiState
}

class CampaignsViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<CampaignsUiState>(CampaignsUiState.Loading)
    val uiState: StateFlow<CampaignsUiState> = _uiState.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<Int>>(emptySet())
    val busyIds: StateFlow<Set<Int>> = _busyIds.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = CampaignsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                // Retired ones too, marked, so they can be brought back.
                CampaignsUiState.Loaded(api.listCampaigns(includeRetired = true))
            } catch (e: Exception) {
                CampaignsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    // Bring a retired campaign back onto the website (retiring happens
    // inside the campaign itself, as for products).
    fun bringBack(campaign: Campaign) {
        val id = campaign.campaignId
        _busyIds.value = _busyIds.value + id
        viewModelScope.launch {
            try {
                api.unretireCampaign(id, actorUserId)
                refresh()
            } catch (e: Exception) {
                _actionError.value = describe(e, "Couldn't bring this campaign back")
            } finally {
                _busyIds.value = _busyIds.value - id
            }
        }
    }

    fun dismissActionError() {
        _actionError.value = null
    }

    // The server's own reason when it gave one ("Marketer, Admin or SuperAdmin only", ...).
    private fun describe(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"(?:detail|message)\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { return it }
        }
        return friendlyError(e, fallback)
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CampaignsViewModel(api, actorUserId) as T
    }
}
