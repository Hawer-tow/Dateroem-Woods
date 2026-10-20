package com.dateroem.woodsales.data.model

// "pictures that can be used to show case what bulk orders look like" - the
// slideshow on the website's Bulk Orders page. Example photos only, never
// products. image is a relative "media/showcase/x.jpg" (see resolveMediaUrl).
data class ShowcasePicture(
    val showcaseId: Int,
    val image: String?,
    val caption: String?
)

// Adding a picture (image = the key the upload returned) or changing its
// caption (image left out). A null caption clears it.
data class ShowcasePictureRequest(
    val image: String? = null,
    val caption: String?
)

// Every picture's id in the new order - all of them, or the server refuses
// (someone else changed the pictures meanwhile).
data class ShowcaseOrderRequest(val showcaseIds: List<Int>)
