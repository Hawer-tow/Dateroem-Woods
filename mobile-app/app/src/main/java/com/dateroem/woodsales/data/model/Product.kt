package com.dateroem.woodsales.data.model

data class Product(
    val productId: Int,
    val name: String?,
    val sku: String?,
    val type: String?,
    val description: String?,
    val price: Double?,
    val stock: Double?,
    val unit: String? = "pieces",
    val attributes: String?,
    // Short marketer/admin-filled badge (e.g. "Most Ordered - Only 2
    // left") shown on the product card itself, apart from Campaigns.
    val marketerNote: String? = null,
    val image: String?,
    val treesId: Int?,
    // "retire a product ... it can be brought back" - hidden from the shop
    // and can't be ordered, but keeps its stock and existing orders. Null
    // (an older server) counts as not retired.
    val isRetired: Boolean? = null
) {
    // Business ask: name can be left empty since the tree Type alone is
    // a fine label - display code should use this instead of `name`
    // directly so a product never shows up blank in a list.
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: type?.takeIf { it.isNotBlank() } ?: "Unnamed product"

    val retired: Boolean
        get() = isRetired == true
}

data class ProductFormRequest(
    // Required as of the naming-by-product-name upload scheme - the
    // stored image file is named after this, so it can no longer be blank.
    val name: String,
    val sku: String?,
    val type: String?,
    val description: String?,
    val price: Double,
    val stock: Double,
    val unit: String,
    val attributes: String?,
    val marketerNote: String?,
    val image: String?,
    val treesId: Int?
)

data class UploadResponse(val key: String)
