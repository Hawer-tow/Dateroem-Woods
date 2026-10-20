package com.dateroem.woodsales.ui.trees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.RemainderTree
import com.dateroem.woodsales.data.model.RemainderTreeRequest
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class RemainderTreeFormState(
    val loading: Boolean = true,
    val recorded: List<RemainderTree> = emptyList(),
    val trees: List<Tree> = emptyList(),
    val farmers: List<Farmer> = emptyList(),
    val selectedOriginTreesId: Int? = null,
    val noTrees: String = "",
    val typeTrees: String = "",
    val totalBodyFeet: String = "",
    val submitting: Boolean = false,
    val error: String? = null
)

// "allow one to select the tree based on the household and farmer id ...
// linking a specific tree (from a certain farmer) to the remainder trees
// is better since one can use this to detect even the exact location of
// the remainder trees by using the coordinates of the originator tree" -
// picking an origin Tree (see RemainderTreeFormScreen's picker) instead of
// just typing numbers. "finalizing trees can be continuous until the
// admin finalizes a trip" - this keeps the recorded list on screen and
// lets another one be added right after, rather than a single one-shot
// form.
class RemainderTreeFormViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _state = MutableStateFlow(RemainderTreeFormState())
    val state: StateFlow<RemainderTreeFormState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // Fetch everything first, *then* do one single
                // read-modify-write of _state.value - seeding the receiver
                // for .copy() from an argument that's still an unresolved
                // suspend call reads a stale snapshot from before any of
                // these awaits, which can silently overwrite whatever
                // another in-flight update just set (see TreeFormViewModel
                // and ExpenseFormViewModel for the same fix).
                val recorded = api.listRemainderTrees(previousTripId = tripId)
                val trees = api.listTrees(tripId)
                val farmers = api.listFarmers()
                _state.value = _state.value.copy(loading = false, recorded = recorded, trees = trees, farmers = farmers)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun selectOriginTree(treesId: Int?) {
        val tree = _state.value.trees.firstOrNull { it.treesId == treesId }
        _state.value = _state.value.copy(
            selectedOriginTreesId = treesId,
            // Prefills from the origin tree's own registration - "how many
            // of that tree's stock is leftover" starts from what it had,
            // still adjustable if only part of it remained.
            noTrees = tree?.noTrees?.toString() ?: _state.value.noTrees,
            typeTrees = tree?.typeTrees ?: _state.value.typeTrees
        )
    }

    fun updateNoTrees(v: String) { _state.value = _state.value.copy(noTrees = v) }
    fun updateTypeTrees(v: String) { _state.value = _state.value.copy(typeTrees = v) }
    fun updateTotalBodyFeet(v: String) { _state.value = _state.value.copy(totalBodyFeet = v) }

    fun addRemainder() {
        val s = _state.value
        if (s.selectedOriginTreesId == null) {
            _state.value = s.copy(error = "Select which tree this remainder came from")
            return
        }
        if (s.noTrees.toIntOrNull() == null) {
            _state.value = s.copy(error = "Number of remainder trees is required")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                api.createRemainderTree(
                    RemainderTreeRequest(
                        previousTripId = tripId,
                        originTreesId = s.selectedOriginTreesId,
                        noTrees = s.noTrees.toIntOrNull(),
                        typeTrees = s.typeTrees.trim().ifBlank { null },
                        totalBodyFeet = s.totalBodyFeet.toDoubleOrNull()
                    )
                )
                // Reset the add-form for the next one, keep the trees/
                // farmers pickers, refresh the recorded list underneath.
                _state.value = _state.value.copy(
                    submitting = false,
                    selectedOriginTreesId = null,
                    noTrees = "",
                    typeTrees = "",
                    totalBodyFeet = ""
                )
                refresh()
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
        return friendlyError(e, "Couldn't save this remainder")
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RemainderTreeFormViewModel(api, tripId) as T
    }
}
