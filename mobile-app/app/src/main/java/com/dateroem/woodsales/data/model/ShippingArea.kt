package com.dateroem.woodsales.data.model

data class ShippingArea(
    val deliveryAreaId: Int,
    val city: String?,
    val areaName: String?,
    val description: String?,
    val isActive: Boolean?
)

data class ShippingAreaRequest(
    val city: String,
    val areaName: String,
    val description: String?,
    val isActive: Boolean
)
