package com.dateroem.woodsales.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.CreateExpenseRequest
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.Machine
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.model.UserOption
import com.dateroem.woodsales.data.model.Vehicle
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class ExpenseFormState(
    val amount: String = "",
    val description: String = "",
    val type: String = "",
    // "machine should be selected from the machine table" / "select the
    // vehicle one used instead of using the vehicle id" - same
    // pick-from-a-real-list treatment as farmers, replacing the old free-
    // typed vehicleUniqueId/machineId text fields. vehicleNotInList +
    // additionalId cover "if its not in the vehicles then one can type
    // the field additional id that is the number plate ... the other id
    // is only filled if the vehicle used was not in the vehicles
    // section" - additionalId is TRANSPORT.Additional_ID, distinct from
    // the VEHICLE-derived Vehicle_Unique_ID.
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: Int? = null,
    val vehicleNotInList: Boolean = false,
    val additionalId: String = "",
    val machines: List<Machine> = emptyList(),
    val selectedMachineId: Int? = null,
    val serialNo: String = "",
    val startDate: String = "",
    val endDate: String = "",
    // "trees are connected to farmer, farmer to farmer payment ... is
    // there a way to connect it to the trees instead which will connect to
    // the farmer" - the picker below is tree-first: selectedTreesId is what
    // TreePickerField sets, and selectTree() reads selectedFarmerId straight
    // off that tree's own Farmer_ID. farmers is unfiltered, kept only so
    // TreePickerField can show a household+phone label instead of a bare id.
    val farmers: List<Farmer> = emptyList(),
    val selectedFarmerId: Int? = null,
    val trees: List<Tree> = emptyList(),
    val selectedTreesId: Int? = null,
    // "connect major expense to a user not just who created it ... who
    // its meant for" - every category except farmer-payments/permits.
    val forUsers: List<UserOption> = emptyList(),
    val selectedForUserId: Int? = null,
    val method: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// Creates an unvalidated holder - business rules: "creates a holder since
// this is stored in the system but not yet validated and then later it
// will be validated by admins" (via the transactions candidates/assign
// flow).
class ExpenseFormViewModel(
    private val api: ApiService,
    private val tripId: Int,
    private val category: ExpenseCategory,
    private val actorUserId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseFormState())
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    init {
        if (category == ExpenseCategory.FARMER_PAYMENTS) {
            viewModelScope.launch {
                try {
                    // Fetch everything first, *then* read-modify-write
                    // _state.value once - reading _state.value before an
                    // awaited suspend call captures a stale snapshot that
                    // can silently clobber other concurrent updates once
                    // this finally resolves (see TreeFormViewModel's
                    // loadPickers() for the bug this pattern caused there).
                    //
                    // "im seeing the select farmer no farmer there ... is
                    // there a way to connect it to the trees instead which
                    // will connect to the farmer which i think is more
                    // efficient" - farmers used to be filtered down to just
                    // those with a tree on this trip, which stayed empty
                    // whenever that filter came up short (every tree here
                    // always has a Farmer_ID - TreeFormViewModel requires
                    // one to submit - so the real bug was in the filter,
                    // not the data). The picker below now picks the TREE
                    // first and reads its farmer straight off the row;
                    // farmers is unfiltered, kept only for TreePickerField's
                    // household/phone labels.
                    val trees = api.listTrees(tripId)
                    val farmers = api.listFarmers()
                    _state.value = _state.value.copy(farmers = farmers, trees = trees)
                } catch (_: Exception) {
                    // Non-fatal - the picker just stays empty.
                }
            }
        }
        if (category == ExpenseCategory.TRANSPORT) {
            viewModelScope.launch {
                try {
                    val vehicles = api.listVehicles()
                    _state.value = _state.value.copy(vehicles = vehicles)
                } catch (_: Exception) {
                    // Non-fatal - falls back to "vehicle not in list".
                }
            }
        }
        if (category == ExpenseCategory.MACHINE_EXPENSES) {
            viewModelScope.launch {
                try {
                    val machines = api.listMachines()
                    _state.value = _state.value.copy(machines = machines)
                } catch (_: Exception) {
                    // Non-fatal - the picker just stays empty.
                }
            }
        }
        if (category in FOR_USER_CATEGORIES) {
            viewModelScope.launch {
                try {
                    val users = api.listUsersForPicker()
                    _state.value = _state.value.copy(forUsers = users)
                } catch (_: Exception) {
                    // Non-fatal - the picker just stays empty (optional field).
                }
            }
        }
    }

    fun updateAmount(v: String) { _state.value = _state.value.copy(amount = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun updateType(v: String) { _state.value = _state.value.copy(type = v) }
    fun selectVehicle(id: Int?) { _state.value = _state.value.copy(selectedVehicleId = id) }
    fun setVehicleNotInList(value: Boolean) {
        _state.value = _state.value.copy(vehicleNotInList = value, selectedVehicleId = if (value) null else _state.value.selectedVehicleId)
    }
    fun updateAdditionalId(v: String) { _state.value = _state.value.copy(additionalId = v) }
    fun selectMachine(id: Int?) { _state.value = _state.value.copy(selectedMachineId = id) }
    fun updateSerialNo(v: String) { _state.value = _state.value.copy(serialNo = v) }
    fun updateStartDate(v: String) { _state.value = _state.value.copy(startDate = v) }
    fun updateEndDate(v: String) { _state.value = _state.value.copy(endDate = v) }
    // Tree-first: picking a tree resolves its own farmer automatically,
    // instead of asking for the farmer up front (see the loading comment
    // above for why that was the broken direction).
    fun selectTree(id: Int?) {
        val s = _state.value
        val farmerId = s.trees.firstOrNull { it.treesId == id }?.farmerId
        _state.value = s.copy(selectedTreesId = id, selectedFarmerId = farmerId)
    }
    fun selectForUser(id: Int?) { _state.value = _state.value.copy(selectedForUserId = id) }
    fun updateMethod(v: String) { _state.value = _state.value.copy(method = v) }

    fun submit() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        if (amount == null) {
            _state.value = s.copy(error = "Enter a valid amount")
            return
        }
        if (category == ExpenseCategory.FARMER_PAYMENTS && s.selectedFarmerId == null) {
            _state.value = s.copy(error = "Select the tree registration this payment is for")
            return
        }
        if (category == ExpenseCategory.TRANSPORT) {
            if (!s.vehicleNotInList && s.selectedVehicleId == null) {
                _state.value = s.copy(error = "Select a vehicle, or mark it as not in the list")
                return
            }
            if (s.vehicleNotInList && s.additionalId.isBlank()) {
                _state.value = s.copy(error = "Enter the vehicle's number plate")
                return
            }
        }
        if (category == ExpenseCategory.MACHINE_EXPENSES && s.selectedMachineId == null) {
            _state.value = s.copy(error = "Select a machine")
            return
        }
        // "who was incharge of the machine at the time ... is not
        // optional but compulsary" - every other FOR_USER_CATEGORIES
        // entry leaves this optional; machine expenses alone requires it.
        if (category == ExpenseCategory.MACHINE_EXPENSES && s.selectedForUserId == null) {
            _state.value = s.copy(error = "Select who was in charge of the machine at the time")
            return
        }
        if (category.hasPaymentMode && s.type != "SOFT" && s.type != "HARD") {
            _state.value = s.copy(error = "Choose whether this was paid with soft or hard cash")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val selectedVehicle = s.vehicles.firstOrNull { it.vehicleId == s.selectedVehicleId }
                val request = CreateExpenseRequest(
                    amount = amount,
                    description = s.description.trim().ifBlank { null },
                    type = s.type.trim().ifBlank { null },
                    vehicleUniqueId = selectedVehicle?.uniqueIdentifier,
                    additionalId = s.additionalId.trim().ifBlank { null },
                    machineId = s.selectedMachineId,
                    serialNo = s.serialNo.trim().ifBlank { null },
                    startDate = s.startDate.trim().ifBlank { null },
                    endDate = s.endDate.trim().ifBlank { null },
                    farmerId = s.selectedFarmerId,
                    treesId = s.selectedTreesId,
                    forUserId = s.selectedForUserId,
                    method = s.method.trim().ifBlank { null }
                )
                api.createExpense(tripId, category.key, request, actorUserId)
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't save this entry")
    }

    class Factory(
        private val api: ApiService,
        private val tripId: Int,
        private val category: ExpenseCategory,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExpenseFormViewModel(api, tripId, category, actorUserId) as T
    }
}

// "do this for all expenses that is farmer expense is different and the
// permits" - every creatable category except FARMER_PAYMENTS (its own
// Farmer_ID/Trees_ID cover "who") and PERMITS.
val FOR_USER_CATEGORIES = setOf(
    ExpenseCategory.TRANSPORT,
    ExpenseCategory.COMMUNICATIONS,
    ExpenseCategory.MACHINE_EXPENSES,
    ExpenseCategory.ACCOMODATION,
    ExpenseCategory.OTHER_EXPENSES,
    ExpenseCategory.SALARIES
)
