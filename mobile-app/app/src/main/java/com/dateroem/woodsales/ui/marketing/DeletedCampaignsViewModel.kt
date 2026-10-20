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
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface DeletedCampaignsUiState {
    data object Loading : DeletedCampaignsUiState
    data class Loaded(val campaigns: List<Campaign>) : DeletedCampaignsUiState
    data class Error(val message: String) : DeletedCampaignsUiState
}

class DeletedCampaignsViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<DeletedCampaignsUiState>(DeletedCampaignsUiState.Loading)
    val uiState: StateFlow<DeletedCampaignsUiState> = _uiState.asStateFlow()

    private val _restoringIds = MutableStateFlow<Set<Int>>(emptySet())
    val restoringIds: StateFlow<Set<Int>> = _restoringIds.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = DeletedCampaignsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                DeletedCampaignsUiState.Loaded(api.listDeletedCampaigns())
            } catch (e: Exception) {
                DeletedCampaignsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun restore(campaignId: Int) {
        _restoringIds.value = _restoringIds.value + campaignId
        viewModelScope.launch {
            try {
                api.restoreCampaign(campaignId, actorUserId)
                refresh()
            } finally {
                _restoringIds.value = _restoringIds.value - campaignId
            }
        }
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeletedCampaignsViewModel(api, actorUserId) as T
    }
}
