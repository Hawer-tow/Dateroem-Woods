package com.dateroem.woodsales.data.model

data class Machine(
    val machineId: Int,
    val name: String?,
    val serialNo: String?,
    val description: String?
)

data class Vehicle(
    val vehicleId: Int,
    val uniqueIdentifier: String?,
    val driver: String?,
    val phoneNumber: String?,
    val description: String?
)
