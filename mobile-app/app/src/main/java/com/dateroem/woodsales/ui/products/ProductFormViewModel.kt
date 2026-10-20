package com.dateroem.woodsales.ui.products

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.media.ImageCompressor
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.Product
import com.dateroem.woodsales.data.model.ProductFormRequest
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import com.dateroem.woodsales.ui.common.friendlyError

enum class ProductUnit(val apiValue: String, val label: String) {
    PIECES("pieces", "Pieces"),
    BOARD_FEET("board_feet", "Board feet (volume)")
}

data class ProductFormState(
    val loading: Boolean = false,
    val name: String = "",
    val type: String = "",
    val description: String = "",
    val price: String = "",
    val quantity: String = "",
    val unit: ProductUnit = ProductUnit.PIECES,
    val marketerNote: String = "",
    // "an area in products to select from trees already registered for a
    // certain trip" - scoped to whichever trip is currently active.
    // farmers backs the picker's "household - phone number" display.
    val trees: List<Tree> = emptyList(),
    val farmers: List<Farmer> = emptyList(),
    val treesId: Int? = null,
    val existingImageUrl: String? = null,
    val pickedImageUri: Uri? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    // Retiring happens here, inside the product, not from the list:
    // "one can click wrong and it doesnt look good being outside".
    val retired: Boolean = false,
    val retiring: Boolean = false,
    val retiredNow: Boolean = false
)

class ProductFormViewModel(
    private val api: ApiService,
    private val actorUserId: Int,
    private val editingProductId: Int?,
    private val activeTripId: Int?,
    // Only admins see "Retire product" (the server checks this too).
    val isAdmin: Boolean = false
) : ViewModel() {

    private val _state = MutableStateFlow(ProductFormState(loading = editingProductId != null))
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    // Key of the image already on the product (if editing) - only
    // resolved to a full URL for preview; re-sent to the server as-is if
    // the user doesn't pick a new photo.
    private var existingImageKey: String? = null

    init {
        activeTripId?.let { tripId ->
            viewModelScope.launch {
                try {
                    // Fetch both first, *then* do one single read-modify-
                    // write of _state.value - seeding the receiver for
                    // .copy() from an argument that's still an unresolved
                    // suspend call reads a stale snapshot from before the
                    // await, which can silently overwrite whatever the
                    // product-loading fetch below just set once this
                    // finally resolves (see TreeFormViewModel's
                    // loadPickers() for the bug this exact pattern caused).
                    val trees = api.listTrees(tripId)
                    val farmers = api.listFarmers()
                    _state.value = _state.value.copy(trees = trees, farmers = farmers)
                } catch (_: Exception) {
                    // Non-fatal - the picker just stays empty.
                }
            }
        }
        editingProductId?.let { id ->
            viewModelScope.launch {
                try {
                    val product = api.getProduct(id)
                    applyLoadedProduct(product)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this product"))
                }
            }
        }
    }

    private fun applyLoadedProduct(product: Product) {
        // mobileServer sends "media/products/x.jpg" (no leading slash), so
        // matching "/media/" never found the key and every edit saved the
        // product with no image ("i edited a product all over sudden the
        // image is lost"). "media/" matches that and any full URL form.
        existingImageKey = product.image?.substringAfter("media/", missingDelimiterValue = "")?.ifBlank { null }
        _state.value = _state.value.copy(
            loading = false,
            name = product.name.orEmpty(),
            type = product.type.orEmpty(),
            description = product.description.orEmpty(),
            price = product.price?.toString().orEmpty(),
            quantity = product.stock?.toString().orEmpty(),
            unit = if (product.unit == ProductUnit.BOARD_FEET.apiValue) ProductUnit.BOARD_FEET else ProductUnit.PIECES,
            marketerNote = product.marketerNote.orEmpty(),
            treesId = product.treesId,
            existingImageUrl = product.image,
            retired = product.retired
        )
    }

    fun updateName(v: String) { _state.value = _state.value.copy(name = v) }
    fun updateType(v: String) { _state.value = _state.value.copy(type = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun updatePrice(v: String) { _state.value = _state.value.copy(price = v) }
    fun updateQuantity(v: String) { _state.value = _state.value.copy(quantity = v) }
    fun updateUnit(v: ProductUnit) { _state.value = _state.value.copy(unit = v) }
    fun updateMarketerNote(v: String) { _state.value = _state.value.copy(marketerNote = v) }
    fun selectTree(id: Int?) { _state.value = _state.value.copy(treesId = id) }
    fun pickImage(uri: Uri) { _state.value = _state.value.copy(pickedImageUri = uri) }

    fun submit(context: Context) {
        val s = _state.value
        val price = s.price.toDoubleOrNull()
        val quantity = s.quantity.toDoubleOrNull()
        if (price == null || quantity == null) {
            _state.value = s.copy(error = "Enter a valid price and quantity")
            return
        }
        if (s.type.isBlank()) {
            _state.value = s.copy(error = "Tree type is required")
            return
        }
        // "the product namepart of the product be used to name the image
        // that goes into the system storage so this will require the
        // product name to be a must" - name is no longer optional.
        if (s.name.isBlank()) {
            _state.value = s.copy(error = "Product name is required - the uploaded photo is named after it")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        val name = s.name.trim()
        viewModelScope.launch {
            try {
                val imageKey = s.pickedImageUri?.let { uploadImage(context, it, name) } ?: existingImageKey
                val request = ProductFormRequest(
                    name = name,
                    sku = null,
                    type = s.type.trim(),
                    description = s.description.trim().ifBlank { null },
                    price = price,
                    stock = quantity,
                    unit = s.unit.apiValue,
                    attributes = null,
                    marketerNote = s.marketerNote.trim().ifBlank { null },
                    image = imageKey,
                    treesId = s.treesId
                )
                if (editingProductId != null) {
                    api.updateProduct(editingProductId, request, actorUserId)
                } else {
                    api.createProduct(request, actorUserId)
                }
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    fun delete() {
        val id = editingProductId ?: return
        _state.value = _state.value.copy(deleting = true, error = null)
        viewModelScope.launch {
            try {
                api.deleteProduct(id, actorUserId)
                _state.value = _state.value.copy(deleting = false, deleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(deleting = false, error = describeError(e))
            }
        }
    }

    // Hide the product from the shop; its stock and orders are kept and it
    // can be brought back from the product list.
    fun retire() {
        val id = editingProductId ?: return
        _state.value = _state.value.copy(retiring = true, error = null)
        viewModelScope.launch {
            try {
                api.retireProduct(id, actorUserId)
                _state.value = _state.value.copy(retiring = false, retired = true, retiredNow = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(retiring = false, error = describeError(e))
            }
        }
    }

    // Retrofit's HttpException.message is just "HTTP 400" - the useful
    // part (which field/why) is in the response body, which this surfaces
    // directly in the form instead of requiring a logcat pull every time.
    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't save this product")
    }

    private suspend fun uploadImage(context: Context, uri: Uri, name: String): String = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        ImageCompressor.compressToJpeg(context, uri, cacheFile)
        val body = cacheFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", cacheFile.name, body)
        api.uploadProductImage(part, name).key
    }

    class Factory(
        private val api: ApiService,
        private val actorUserId: Int,
        private val editingProductId: Int?,
        private val activeTripId: Int? = null,
        private val isAdmin: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProductFormViewModel(api, actorUserId, editingProductId, activeTripId, isAdmin) as T
    }
}
