package com.dateroem.woodsales.ui.marketing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.media.ImageCompressor
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.data.model.CampaignFormRequest
import com.dateroem.woodsales.data.model.CampaignMediaUploadResponse
import com.dateroem.woodsales.data.model.CampaignProductsRequest
import com.dateroem.woodsales.data.model.Product
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

enum class CampaignStatus(val label: String) {
    DRAFT("Draft"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed")
}

// "can we have a selector for channel not typing but having a radio box
// for the channel options or an all if one wants to send it to all areas" -
// a fixed set instead of the free-typed field it replaces; Channel stays a
// plain string column on CAMPAIGN, so ALL is just stored as the literal
// "All" like any other option, not a separate concept.
enum class CampaignChannel(val value: String) {
    TIKTOK("TikTok"),
    SMS("SMS"),
    IN_STORE("In-store"),
    ALL("All")
}

const val MEDIA_TYPE_VIDEO = "VIDEO"
const val MEDIA_TYPE_IMAGE = "IMAGE"

// The bare media key the backend stores ("campaigns/x.mp4"), from however
// the address reached the app: "media/campaigns/x.mp4" from the mobile
// server, or a full address. The old version looked for "/media/" with a
// leading slash, never matched the mobile server's form, and so every edit
// saved the campaign without its video ("the video is gone").
internal fun mediaKeyOf(address: String?): String? {
    if (address.isNullOrBlank()) return null
    val slashed = address.indexOf("/media/")
    val key = when {
        slashed >= 0 -> address.substring(slashed + "/media/".length)
        address.startsWith("media/") -> address.removePrefix("media/")
        else -> address
    }
    return key.substringBefore('?').ifBlank { null }
}

// "make sure the remembrance of the media is remembered" - a campaign form
// left without saving (another section opened, the phone put away) keeps
// what was typed, ticked and picked for as long as the app runs, and the
// form brings it back next time. Saving, Cancel or deleting forgets it.
private data class CampaignDraft(
    val name: String,
    val channel: String,
    val startAt: String,
    val endAt: String,
    val status: CampaignStatus,
    val pickedMediaUri: Uri?,
    val pickedMediaType: String?,
    // null when the product list never loaded - the campaign's own stand.
    val selectedProductIds: Set<Int>?
)

private object CampaignDrafts {
    const val NEW_CAMPAIGN = -1
    private val drafts = mutableMapOf<Int, CampaignDraft>()

    @Synchronized fun get(key: Int): CampaignDraft? = drafts[key]
    @Synchronized fun put(key: Int, draft: CampaignDraft) { drafts[key] = draft }
    @Synchronized fun remove(key: Int) { drafts.remove(key) }
}

data class CampaignFormState(
    val loading: Boolean = false,
    val name: String = "",
    val channel: String = "",
    val startAt: String = "",
    val endAt: String = "",
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val existingMediaUrl: String? = null,
    val existingMediaType: String? = null,
    val pickedMediaUri: Uri? = null,
    val pickedMediaType: String? = null,
    // "Products in this campaign" - the website shows a campaign only once
    // it has at least one. productsLoaded guards saving: if the list never
    // loaded, the campaign's products are left as they were.
    val products: List<Product> = emptyList(),
    val productsLoaded: Boolean = false,
    val productsError: String? = null,
    val selectedProductIds: Set<Int> = emptySet(),
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    // Unsaved changes from an earlier visit were put back.
    val restoredDraft: Boolean = false
) {
    // Whichever is about to be saved - a freshly picked file takes
    // priority over whatever the campaign already had.
    val effectiveMediaType: String?
        get() = pickedMediaType ?: existingMediaType
}

class CampaignFormViewModel(
    private val api: ApiService,
    private val actorUserId: Int,
    private val editingCampaignId: Int?
) : ViewModel() {

    private val _state = MutableStateFlow(CampaignFormState(loading = editingCampaignId != null))
    val state: StateFlow<CampaignFormState> = _state.asStateFlow()

    // Same scheme as ProductFormViewModel.existingImageKey - only resolved
    // to a full URL for the preview; re-sent as-is if the user doesn't
    // pick a new file.
    private var existingMediaKey: String? = null

    // Set once the campaign is saved, so a retry (say its products failed
    // to save) updates that campaign instead of creating a second one.
    private var savedCampaignId: Int? = editingCampaignId

    // A retry reuses the file already uploaded for this pick.
    private var uploadedPick: Pair<Uri, CampaignMediaUploadResponse>? = null

    private val draftKey = editingCampaignId ?: CampaignDrafts.NEW_CAMPAIGN
    private var draft: CampaignDraft? = CampaignDrafts.get(draftKey)
    // Anything typed, ticked or picked since the form opened.
    private var changed = false
    // Saved, deleted or cancelled - nothing to keep.
    private var finished = false

    init {
        load()
    }

    private fun load() {
        if (editingCampaignId == null) {
            draft?.let(::applyDraft)
        } else {
            viewModelScope.launch {
                try {
                    val campaign = api.getCampaign(editingCampaignId)
                    applyLoadedCampaign(campaign)
                    draft?.let(::applyDraft)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this campaign"))
                }
            }
        }
        loadProducts()
    }

    private fun applyDraft(d: CampaignDraft) {
        changed = true
        _state.value = _state.value.copy(
            name = d.name,
            channel = d.channel,
            startAt = d.startAt,
            endAt = d.endAt,
            status = d.status,
            pickedMediaUri = d.pickedMediaUri,
            pickedMediaType = d.pickedMediaType,
            restoredDraft = true
        )
    }

    // "Discard" on the restored-changes notice: back to the campaign as saved.
    fun discardDraft() {
        CampaignDrafts.remove(draftKey)
        draft = null
        changed = false
        uploadedPick = null
        _state.value = CampaignFormState(loading = editingCampaignId != null)
        load()
    }

    // Cancel means "leave it as it was" - nothing is kept for next time.
    fun forgetChanges() {
        finished = true
        CampaignDrafts.remove(draftKey)
    }

    override fun onCleared() {
        // A new campaign already created here (only its products failed)
        // must not come back as a draft and be created twice.
        val createdHere = editingCampaignId == null && savedCampaignId != null
        if (!finished && changed && !createdHere) {
            val s = _state.value
            CampaignDrafts.put(
                draftKey,
                CampaignDraft(
                    name = s.name,
                    channel = s.channel,
                    startAt = s.startAt,
                    endAt = s.endAt,
                    status = s.status,
                    pickedMediaUri = s.pickedMediaUri,
                    pickedMediaType = s.pickedMediaType,
                    selectedProductIds = if (s.productsLoaded) s.selectedProductIds else null
                )
            )
        }
    }

    private fun loadProducts() {
        _state.value = _state.value.copy(productsError = null)
        viewModelScope.launch {
            try {
                val products = api.listProducts()
                val linked = draft?.selectedProductIds
                    ?: editingCampaignId?.let { api.campaignProductIds(it) }.orEmpty()
                // Links to products in the bin aren't shown; the server
                // leaves them alone when this campaign is saved.
                val liveIds = products.map { it.productId }.toSet()
                _state.value = _state.value.copy(
                    products = products,
                    productsLoaded = true,
                    selectedProductIds = linked.filter { it in liveIds }.toSet()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(productsError = "Couldn't load the products: ${describeError(e)}")
            }
        }
    }

    fun retryProducts() = loadProducts()

    private fun applyLoadedCampaign(campaign: Campaign) {
        existingMediaKey = mediaKeyOf(campaign.media)
        _state.value = _state.value.copy(
            loading = false,
            name = campaign.name,
            channel = campaign.channel.orEmpty(),
            startAt = campaign.startAt.orEmpty(),
            endAt = campaign.endAt.orEmpty(),
            status = CampaignStatus.entries.firstOrNull { it.label.equals(campaign.status, ignoreCase = true) }
                ?: CampaignStatus.DRAFT,
            existingMediaUrl = campaign.media,
            existingMediaType = campaign.mediaType
        )
    }

    private fun edit(change: CampaignFormState.() -> CampaignFormState) {
        changed = true
        _state.value = _state.value.change()
    }

    fun updateName(v: String) = edit { copy(name = v) }
    fun updateChannel(v: String) = edit { copy(channel = v) }
    fun updateStartAt(v: String) = edit { copy(startAt = v) }
    fun updateEndAt(v: String) = edit { copy(endAt = v) }
    fun updateStatus(v: CampaignStatus) = edit { copy(status = v) }
    fun setSelectedProducts(ids: Set<Int>) = edit { copy(selectedProductIds = ids) }

    // Determined from the picked file itself (its real MIME type), not
    // which button launched the picker - the gallery option can hand back
    // either kind. "in campaign only one of the medias can be used ...
    // when one wants to edit you can change from one media to another" -
    // picking a new file of either kind simply replaces whatever was there.
    fun pickMedia(context: Context, uri: Uri) {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val type = if (mimeType.startsWith("video/")) MEDIA_TYPE_VIDEO else MEDIA_TYPE_IMAGE
        edit { copy(pickedMediaUri = uri, pickedMediaType = type) }
    }

    fun submit(context: Context) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(error = "Campaign name is required")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        val name = s.name.trim()
        viewModelScope.launch {
            try {
                var mediaKey = existingMediaKey
                var mediaType = s.existingMediaType
                val pickedUri = s.pickedMediaUri
                if (pickedUri != null) {
                    val uploaded = uploadedPick?.takeIf { it.first == pickedUri }?.second
                        ?: uploadMedia(context, pickedUri, s.pickedMediaType ?: MEDIA_TYPE_IMAGE, name)
                            .also { uploadedPick = pickedUri to it }
                    mediaKey = uploaded.key
                    mediaType = uploaded.mediaType
                }
                val request = CampaignFormRequest(
                    name = name,
                    channel = s.channel.trim().ifBlank { null },
                    startAt = s.startAt.trim().ifBlank { null },
                    endAt = s.endAt.trim().ifBlank { null },
                    status = s.status.label,
                    media = mediaKey,
                    mediaType = if (mediaKey == null) null else mediaType
                )
                val campaignId = savedCampaignId?.also { api.updateCampaign(it, request, actorUserId) }
                    ?: api.createCampaign(request, actorUserId).campaignId.also { savedCampaignId = it }
                if (s.productsLoaded) {
                    try {
                        api.setCampaignProducts(
                            campaignId,
                            CampaignProductsRequest(s.selectedProductIds.sorted()),
                            actorUserId
                        )
                    } catch (e: Exception) {
                        _state.value = _state.value.copy(
                            submitting = false,
                            error = "The campaign was saved, but its products weren't: ${describeError(e)}. Tap save to try again."
                        )
                        return@launch
                    }
                }
                finished = true
                CampaignDrafts.remove(draftKey)
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    fun delete() {
        val id = editingCampaignId ?: return
        _state.value = _state.value.copy(deleting = true, error = null)
        viewModelScope.launch {
            try {
                api.deleteCampaign(id, actorUserId)
                finished = true
                CampaignDrafts.remove(draftKey)
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
        return friendlyError(e, "Couldn't save this campaign")
    }

    private suspend fun uploadMedia(
        context: Context,
        uri: Uri,
        mediaType: String,
        name: String
    ): CampaignMediaUploadResponse = withContext(Dispatchers.IO) {
        val part = if (mediaType == MEDIA_TYPE_VIDEO) {
            // Videos aren't re-encoded on-device (no compression pass,
            // unlike images) - just streamed into a cache file so
            // Retrofit has a real File to build the multipart request
            // from; size/type validation happens server-side.
            val extension = guessVideoExtension(context, uri)
            val cacheFile = File(context.cacheDir, "campaign_upload_${System.currentTimeMillis()}$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Couldn't read the picked video")
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            val body = cacheFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", cacheFile.name, body)
        } else {
            val cacheFile = File(context.cacheDir, "campaign_upload_${System.currentTimeMillis()}.jpg")
            ImageCompressor.compressToJpeg(context, uri, cacheFile)
            val body = cacheFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", cacheFile.name, body)
        }
        api.uploadCampaignMedia(part, name)
    }

    private fun guessVideoExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "video/quicktime" -> ".mov"
            "video/webm" -> ".webm"
            "video/3gpp" -> ".3gp"
            else -> ".mp4"
        }
    }

    class Factory(
        private val api: ApiService,
        private val actorUserId: Int,
        private val editingCampaignId: Int?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CampaignFormViewModel(api, actorUserId, editingCampaignId) as T
    }
}
