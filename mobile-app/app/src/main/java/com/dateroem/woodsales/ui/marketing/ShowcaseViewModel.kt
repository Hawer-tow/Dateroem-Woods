package com.dateroem.woodsales.ui.marketing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.media.ImageCompressor
import com.dateroem.woodsales.data.model.ShowcaseOrderRequest
import com.dateroem.woodsales.data.model.ShowcasePicture
import com.dateroem.woodsales.data.model.ShowcasePictureRequest
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
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import com.dateroem.woodsales.ui.common.friendlyError

// Same limits as the server (backend ShowcaseService).
const val SHOWCASE_MAX_PICTURES = 12
const val SHOWCASE_MAX_CAPTION = 120

data class ShowcaseState(
    val loading: Boolean = true,
    val pictures: List<ShowcasePicture> = emptyList(),
    // A change being saved - the buttons wait meanwhile.
    val busy: Boolean = false,
    val loadError: String? = null,
    val error: String? = null
)

class ShowcaseViewModel(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(ShowcaseState())
    val state: StateFlow<ShowcaseState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = _state.value.pictures.isEmpty(), loadError = null)
        viewModelScope.launch {
            _state.value = try {
                _state.value.copy(loading = false, pictures = api.listShowcase())
            } catch (e: Exception) {
                _state.value.copy(loading = false, loadError = describe(e))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // Shrunk on the phone first (1600 px JPEG, like product photos), named
    // after the caption, then added at the end of the slideshow.
    fun add(context: Context, uri: Uri, caption: String?) = change {
        val cleaned = clean(caption)
        val key = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "showcase_${System.currentTimeMillis()}.jpg")
            try {
                ImageCompressor.compressToJpeg(context, uri, file)
                val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                api.uploadShowcasePicture(part, cleaned ?: "bulk-order").key
            } finally {
                file.delete()
            }
        }
        api.addShowcasePicture(ShowcasePictureRequest(image = key, caption = cleaned))
    }

    fun editCaption(showcaseId: Int, caption: String?) = change {
        api.updateShowcaseCaption(showcaseId, ShowcasePictureRequest(caption = clean(caption)))
    }

    // step -1 = earlier in the slideshow, +1 = later.
    fun move(index: Int, step: Int) {
        val ids = _state.value.pictures.map { it.showcaseId }.toMutableList()
        val target = index + step
        if (index !in ids.indices || target !in ids.indices) return
        ids[index] = ids[target].also { ids[target] = ids[index] }
        change { api.reorderShowcase(ShowcaseOrderRequest(ids)) }
    }

    fun remove(showcaseId: Int) = change { api.removeShowcasePicture(showcaseId) }

    // Runs one change, then shows the pictures as they now are - also after
    // a refusal (e.g. someone else changed them meanwhile).
    private fun change(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: Exception) {
                describe(e)
            }
            val pictures = try {
                api.listShowcase()
            } catch (_: Exception) {
                _state.value.pictures
            }
            _state.value = _state.value.copy(busy = false, pictures = pictures, error = error)
        }
    }

    private fun clean(caption: String?): String? =
        caption?.trim()?.split(Regex("\\s+"))?.joinToString(" ")?.take(SHOWCASE_MAX_CAPTION)?.ifBlank { null }

    // The server's own words ("Keep the caption to 120 characters", "At most
    // 12 pictures ..."), not just "HTTP 409".
    private fun describe(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            val detail = runCatching { JSONObject(body ?: "").optString("detail") }.getOrNull()
            return detail?.takeIf { it.isNotBlank() } ?: friendlyError(e, "Couldn't save")
        }
        return friendlyError(e, "Couldn't reach the server")
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShowcaseViewModel(api) as T
    }
}
