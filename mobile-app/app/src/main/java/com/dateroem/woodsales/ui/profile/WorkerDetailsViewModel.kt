package com.dateroem.woodsales.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.media.ImageCompressor
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

data class WorkerDetailsState(
    val loading: Boolean = true,
    val error: String? = null,
    val record: JsonMap? = null,
    val saving: Boolean = false,
    val saveNote: String? = null,
    val saveError: String? = null,
    val uploading: String? = null,
    val uploadError: String? = null
)

/**
 * "worker ID number and documents with ... verification" (migration 040): the
 * staff member fills in their own details and photographs their documents;
 * the SuperAdmin checks them and marks them verified, or sends them back with
 * a note.
 */
class WorkerDetailsViewModel(private val api: ApiService, private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow(WorkerDetailsState())
    val state: StateFlow<WorkerDetailsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                _state.value.copy(loading = false, error = null, record = api.myWorkerDetails())
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = describe(e))
            }
        }
    }

    fun save(values: Map<String, String?>) {
        _state.value = _state.value.copy(saving = true, saveNote = null, saveError = null)
        viewModelScope.launch {
            try {
                val record = api.saveMyWorkerDetails(mapOf("values" to values))
                _state.value = _state.value.copy(saving = false, record = record, saveNote = "Saved.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, saveError = describe(e))
            }
        }
    }

    fun upload(typeKey: String, uri: Uri) {
        _state.value = _state.value.copy(uploading = typeKey, uploadError = null)
        viewModelScope.launch {
            val file = File(appContext.cacheDir, "worker-document.jpg")
            try {
                ImageCompressor.compressToJpeg(appContext, uri, file, maxDimension = 1600)
                val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                val record = api.uploadWorkerDocument(typeKey, part)
                _state.value = _state.value.copy(uploading = null, record = record)
            } catch (e: Exception) {
                _state.value = _state.value.copy(uploading = null, uploadError = describe(e))
            } finally {
                file.delete()
            }
        }
    }

    class Factory(private val api: ApiService, private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkerDetailsViewModel(api, appContext) as T
    }
}
