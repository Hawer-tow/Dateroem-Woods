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

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class NoTripSelected(val companyInfo: CompanyInfo, val campaigns: DashboardCampaignStats) : DashboardUiState
    data class TripSelected(val stats: DashboardTripStats, val campaigns: DashboardCampaignStats) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

// "i want to add a layout the defaullt where everything comes to here we
// will have the profits made from a trip total expenses of the trip total
// products sold total deliveries ... campaigns done" - this is the app's
// new landing screen. It follows whatever trip the bottom wood-dial has
// picked (ActiveTripStore) rather than asking every time; with none picked
// it falls back to company info only, per the same instruction.
class DashboardViewModel(
    private val api: ApiService,
    private val activeTripStore: ActiveTripStore,
    private val companyInfoStore: CompanyInfoStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activeTripStore.activeTripId.collect { tripId -> load(tripId) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(activeTripStore.activeTripId.first()) }
    }

    private suspend fun load(tripId: Int?) {
        _uiState.value = DashboardUiState.Loading
        _uiState.value = try {
            val campaigns = try {
                val list = api.listCampaigns()
                DashboardCampaignStats(total = list.size, completed = list.count { it.status == "Completed" })
            } catch (e: Exception) {
                DashboardCampaignStats(0, 0)
            }
            if (tripId == null) {
                DashboardUiState.NoTripSelected(companyInfoStore.companyInfo.first(), campaigns)
            } else {
                try {
                    DashboardUiState.TripSelected(loadTripStats(tripId), campaigns)
                } catch (e: HttpException) {
                    // "still getting the 404 error not the 500 now" - a
                    // trip id saved from before the data reset no longer
                    // exists; the backend now says so cleanly (404)
                    // instead of crashing, but showing that as a raw
                    // error was just as unhelpful. The real fix is to
                    // stop pointing at a trip that's gone and fall back
                    // to the ordinary "no trip picked yet" screen.
                    if (e.code() == 404) {
                        activeTripStore.setActiveTrip(null)
                        DashboardUiState.NoTripSelected(companyInfoStore.companyInfo.first(), campaigns)
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            DashboardUiState.Error(friendlyError(e, "Couldn't load this right now"))
        }
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

    fun saveCompanyInfo(info: CompanyInfo) {
        viewModelScope.launch {
            companyInfoStore.save(info)
            refresh()
        }
    }

    class Factory(
        private val api: ApiService,
        private val activeTripStore: ActiveTripStore,
        private val companyInfoStore: CompanyInfoStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(api, activeTripStore, companyInfoStore) as T
    }
}
