package com.dateroem.woodsales.data.model

data class Campaign(
    val campaignId: Int,
    val name: String,
    val channel: String?,
    val startAt: String?,
    val endAt: String?,
    val status: String?,
    // Relative-key-resolved-to-full-URL, same scheme as Product.image - at
    // most one media item per campaign, either a video or an image, never
    // both. mediaType ("VIDEO"/"IMAGE") says which, so the UI knows how to
    // render it without guessing from the file extension.
    val media: String?,
    val mediaType: String?,
    // "allow a campaign to be retired just like the products" - off the
    // website, but kept with its media and products, and can be brought
    // back. Null (an older server) counts as not retired.
    val isRetired: Boolean? = null
) {
    val retired: Boolean
        get() = isRetired == true
}

data class CampaignFormRequest(
    val name: String,
    val channel: String?,
    val startAt: String?,
    val endAt: String?,
    val status: String?,
    val media: String?,
    val mediaType: String?
)

data class CampaignMediaUploadResponse(val key: String, val mediaType: String)

// "Products in this campaign": every product the campaign promotes,
// replacing what was linked before (an empty list removes them all).
data class CampaignProductsRequest(val productIds: List<Int>)
