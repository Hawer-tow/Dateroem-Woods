package com.dateroem.woodsales.ui.trees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.FarmerRequest
import com.dateroem.woodsales.data.model.RemainderTree
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.model.TreeRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class TreeFormState(
    val loading: Boolean = false,
    val houseHold: String = "",
    val coordinates: String = "",
    val noTrees: String = "",
    val typeTrees: String = "",
    val active: Boolean = true,
    val farmers: List<Farmer> = emptyList(),
    val selectedFarmerId: Int? = null,
    // "register a new farmer instead" mode - creates the FARMER row right
    // before the TREES row, in one submit.
    val registeringNewFarmer: Boolean = false,
    val newFarmerName: String = "",
    val newFarmerPhone: String = "",
    val remainderTrees: List<RemainderTree> = emptyList(),
    val selectedRemainderTreesId: Int? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false
)

// Create-or-edit for one TREES row - "select from trees already
// registered for a certain trip", plus the farmer it belongs to and an
// optional link to a REMAINDER_TREES record carried forward from an
// earlier trip ("pick other remainder tree to a tree item in another trip").
class TreeFormViewModel(
    private val api: ApiService,
    private val tripId: Int,
    private val actorUserId: Int,
    private val editingTreesId: Int?
) : ViewModel() {

    private val _state = MutableStateFlow(TreeFormState())
    val state: StateFlow<TreeFormState> = _state.asStateFlow()

    init {
        loadPickers()
        editingTreesId?.let { id ->
            _state.value = _state.value.copy(loading = true)
            viewModelScope.launch {
                try {
                    // No single-tree GET on the client side yet - the trip's
                    // full tree list is small, so find it there.
                    val tree = api.listTrees(tripId).firstOrNull { it.treesId == id }
                    if (tree != null) applyLoadedTree(tree)
                    _state.value = _state.value.copy(loading = false)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this tree"))
                }
            }
        }
    }

    private fun loadPickers() {
        // Fetch first, *then* read-modify-write _state.value - not the
        // other way around. `_state.value.copy(farmers = api.listFarmers())`
        // reads the (receiver) _state.value before suspending on the
        // network call, so if the tree-loading fetch below finishes first
        // and fills in houseHold/coordinates/etc., this would overwrite
        // that with a copy taken from the stale, still-empty snapshot from
        // before the suspension - wiping the just-loaded tree back to
        // blank. That's the exact "everything empty" bug this fixes.
        viewModelScope.launch {
            try {
                val farmers = api.listFarmers()
                _state.value = _state.value.copy(farmers = farmers)
            } catch (_: Exception) {
                // Non-fatal - the picker just stays empty, "New farmer" still works.
            }
        }
        viewModelScope.launch {
            try {
                val remainderTrees = api.listRemainderTrees()
                _state.value = _state.value.copy(remainderTrees = remainderTrees)
            } catch (_: Exception) {
            }
        }
    }

    private fun applyLoadedTree(tree: Tree) {
        _state.value = _state.value.copy(
            houseHold = tree.houseHold.orEmpty(),
            coordinates = tree.coordinates.orEmpty(),
            noTrees = tree.noTrees?.toString().orEmpty(),
            typeTrees = tree.typeTrees.orEmpty(),
            active = tree.active != false,
            selectedFarmerId = tree.farmerId,
            selectedRemainderTreesId = tree.remainderTreesId
        )
    }

    fun updateHouseHold(v: String) { _state.value = _state.value.copy(houseHold = v) }
    fun updateCoordinates(v: String) { _state.value = _state.value.copy(coordinates = v) }
    fun updateNoTrees(v: String) { _state.value = _state.value.copy(noTrees = v) }
    fun updateTypeTrees(v: String) { _state.value = _state.value.copy(typeTrees = v) }
    fun updateActive(v: Boolean) { _state.value = _state.value.copy(active = v) }
    fun selectFarmer(id: Int?) { _state.value = _state.value.copy(selectedFarmerId = id) }
    fun setRegisteringNewFarmer(v: Boolean) { _state.value = _state.value.copy(registeringNewFarmer = v, selectedFarmerId = null) }
    fun updateNewFarmerName(v: String) { _state.value = _state.value.copy(newFarmerName = v) }
    fun updateNewFarmerPhone(v: String) { _state.value = _state.value.copy(newFarmerPhone = v) }
    fun selectRemainderTrees(id: Int?) { _state.value = _state.value.copy(selectedRemainderTreesId = id) }

    fun submit() {
        val s = _state.value
        if (s.noTrees.toIntOrNull() == null) {
            _state.value = s.copy(error = "Number of trees is required")
            return
        }
        if (s.registeringNewFarmer && s.newFarmerName.isBlank()) {
            _state.value = s.copy(error = "New farmer's name is required")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val farmerId = if (s.registeringNewFarmer) {
                    api.createFarmer(
                        FarmerRequest(
                            name = s.newFarmerName.trim(),
                            phoneNo = s.newFarmerPhone.trim().ifBlank { null },
                            bankDetailsMarked = null,
                            balance = 0.0
                        )
                    ).farmerId
                } else {
                    s.selectedFarmerId
                }

                val request = TreeRequest(
                    houseHold = s.houseHold.trim().ifBlank { null },
                    coordinates = s.coordinates.trim().ifBlank { null },
                    noTrees = s.noTrees.toIntOrNull(),
                    typeTrees = s.typeTrees.trim().ifBlank { null },
                    active = s.active,
                    remainderTreesId = s.selectedRemainderTreesId,
                    farmerId = farmerId,
                    tripId = tripId
                )
                if (editingTreesId != null) {
                    api.updateTree(editingTreesId, request)
                } else {
                    api.createTree(request)
                }
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    // "delete trees should also be created and add a bin just like the
    // product section does it" - only reachable while editing, same as
    // ProductFormViewModel's own delete.
    fun delete() {
        val id = editingTreesId ?: return
        _state.value = _state.value.copy(deleting = true, error = null)
        viewModelScope.launch {
            try {
                api.deleteTree(id, actorUserId)
                _state.value = _state.value.copy(deleting = false, deleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(deleting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { return it.groupValues[1] }
            return friendlyError(e, "Something went wrong. Please try again.")
        }
        return friendlyError(e, "Couldn't save this tree registration")
    }

    class Factory(
        private val api: ApiService,
        private val tripId: Int,
        private val actorUserId: Int,
        private val editingTreesId: Int?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TreeFormViewModel(api, tripId, actorUserId, editingTreesId) as T
    }
}
