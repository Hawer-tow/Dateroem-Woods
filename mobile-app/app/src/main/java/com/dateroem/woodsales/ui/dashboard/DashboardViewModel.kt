package com.dateroem.woodsales.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.data.session.ActiveTripStore
import com.dateroem.woodsales.data.session.CompanyInfo
import com.dateroem.woodsales.data.session.CompanyInfoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class DashboardTripStats(
    val trip: JsonMap,
    val profit: Double,
    val totalExpenses: Double,
    val productsSold: Int,
    val bulkDeliveries: Int,
    val lightDeliveries: Int
)

data class DashboardCampaignStats(val total: Int, val completed: Int)

/**
 * Everything Home shows: the company card (everyone), the business at a
 * glance (Admins and the SuperAdmin), the picked trip's figures and campaigns.
 */
data class DashboardData(
    val company: CompanyInfo,
    val overview: JsonMap?,
    val trip: DashboardTripStats?,
    val campaigns: DashboardCampaignStats
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Loaded(val data: DashboardData) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

// "i want to add a layout the defaullt where everything comes to here we
// will have the profits made from a trip total expenses of the trip total
// products sold total deliveries ... campaigns done" - this is the app's
// landing screen. It follows whatever trip the bottom wood-dial has picked
// (ActiveTripStore) rather than asking every time.
//
// 24 October 2026: "superadmin in mobile cant seem to see the other details"
// - Admins and the SuperAdmin also see the business at a glance (the
// desktop dashboard's figures, /insights/overview). The company card comes
// from the server, the same on every phone ("only superadmin edits ... others
// see the motto"), and is kept on the phone for when it is offline.
class DashboardViewModel(
    private val api: ApiService,
    private val activeTripStore: ActiveTripStore,
    private val companyInfoStore: CompanyInfoStore,
    private val isAdmin: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            activeTripStore.activeTripId.collect { tripId -> load(tripId) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(activeTripStore.activeTripId.first()) }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun load(tripId: Int?) {
        if (_uiState.value !is DashboardUiState.Loaded) _uiState.value = DashboardUiState.Loading
        _uiState.value = try {
            val campaigns = try {
                val list = api.listCampaigns()
                DashboardCampaignStats(total = list.size, completed = list.count { it.status == "Completed" })
            } catch (e: Exception) {
                DashboardCampaignStats(0, 0)
            }
            val overview = if (isAdmin) runCatching { api.overview() }.getOrNull() else null
            val trip = if (tripId == null) null else try {
                loadTripStats(tripId)
            } catch (e: HttpException) {
                // "still getting the 404 error not the 500 now" - a trip id
                // saved from before the data reset no longer exists; stop
                // pointing at it and show Home without a trip.
                if (e.code() == 404) {
                    activeTripStore.setActiveTrip(null)
                    null
                } else {
                    throw e
                }
            }
            DashboardUiState.Loaded(DashboardData(company(), overview, trip, campaigns))
        } catch (e: Exception) {
            DashboardUiState.Error(friendlyError(e, "Couldn't load this right now"))
        }
    }

    // The server's card, kept on the phone; the kept one when offline.
    private suspend fun company(): CompanyInfo = try {
        val card = api.companyCard()
        val info = CompanyInfo(
            name = card["name"] as? String ?: "",
            motto = card["motto"] as? String ?: "",
            code = card["code"] as? String ?: ""
        )
        companyInfoStore.save(info)
        info
    } catch (e: Exception) {
        companyInfoStore.companyInfo.first()
    }

    private suspend fun loadTripStats(tripId: Int): DashboardTripStats {
        val trip = api.getTrip(tripId)
        val summary = api.tripSummary(tripId)
        val deliveries = api.tripDeliveries(tripId)

        val expenseKeys = listOf(
            "farmerPaymentTotal", "salaryTotal", "accomodationTotal", "transportTotal",
            "communicationTotal", "machineExpenseTotal", "otherExpenseTotal", "permitTotal"
        )
        val totalExpenses = expenseKeys.sumOf { key -> (summary[key] as? Number)?.toDouble() ?: 0.0 }

        return DashboardTripStats(
            trip = trip,
            profit = (summary["profit"] as? Number)?.toDouble() ?: 0.0,
            totalExpenses = totalExpenses,
            productsSold = (summary["productsSold"] as? Number)?.toInt() ?: 0,
            bulkDeliveries = (deliveries["bulkCount"] as? Number)?.toInt() ?: 0,
            lightDeliveries = (deliveries["lightCount"] as? Number)?.toInt() ?: 0
        )
    }

    /** SuperAdmin only (the server checks): the card every phone shows. */
    fun saveCompanyInfo(info: CompanyInfo) {
        viewModelScope.launch {
            try {
                api.saveCompanyCard(mapOf("name" to info.name, "motto" to info.motto, "code" to info.code))
                companyInfoStore.save(info)
                refresh()
            } catch (e: Exception) {
                _message.value = friendlyError(e, "Couldn't save the company card")
            }
        }
    }

    class Factory(
        private val api: ApiService,
        private val activeTripStore: ActiveTripStore,
        private val companyInfoStore: CompanyInfoStore,
        private val isAdmin: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(api, activeTripStore, companyInfoStore, isAdmin) as T
    }
}
