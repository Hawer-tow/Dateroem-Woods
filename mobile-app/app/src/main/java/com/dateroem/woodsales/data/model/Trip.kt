package com.dateroem.woodsales.data.model

// "this is the section where i want one to create a trip so that then the
// rest of the sections are active since almost all are connected to the
// trip factor".
data class CreateTripRequest(val startDate: String, val productDescription: String?)

data class UpdateTripRequest(
    val endDate: String?,
    val noOfDeliveries: Int?,
    val totalBodyFeet: Double?,
    val productDescription: String?
)

// "the hardcash part is written manually" - the trip-level split.
data class CashSplitRequest(val hardCash: Double, val softCash: Double)

// "i want to allow the admin or staff to create a bulk order and its
// added into the system and if it fails its just deleted".
data class CreateBulkOrderRequest(val deliveredTo: String?, val tripId: Int)

// "once the bulk delivery is delivered the details are filled on amount
// cash soft which is filled into the bulk delivery as well as the bulk
// delivery description table".
data class FinalizeBulkDeliveryRequest(
    val hardCashAmount: Double,
    val softCashAmount: Double,
    val productType: String?,
    val productDescription: String?,
    val productMeasurement: String?,
    val totalBodyFeet: Double?,
    val productTotal: Double?
)

data class LightDeliveryStatusRequest(val status: String)

// "add a signature area which is stored in the signature blob for the
// said products for the person to be added to the database" - a base64
// PNG data URL captured from the phone's signature pad, plus who signed.
data class DeliverLightDeliveryRequest(val signature: String?, val receivedBy: String?)

// "you can never create a new trip before you finalize every detail in the
// other trip" - dropUndelivered sends the orders still undelivered back to
// the pending list (as priority) so the trip can close.
data class FinalizeTripRequest(val dropUndelivered: Boolean)

// Orders (pending, grouped per customer) -> a delivery.
// Exactly one target: a bulk delivery (side quests) or a light delivery to
// group under; both null just assigns to the trip.
data class AssignBatchRequest(
    val lightDeliveryIds: List<Int>,
    val tripId: Int?,
    val vehicleId: Int?,
    val estimatedDeliveryDate: String?,
    val bulkDeliveryId: Int?,
    val parentLightDeliveryId: Int?
)

// Which orders on a delivery arrived and which didn't (and why).
data class SettleRequest(val deliveredIds: List<Int>, val undeliveredIds: List<Int>, val reason: String?)

data class CreateLightDeliveryRequest(
    val tripId: Int,
    val vehicleId: Int?,
    val deliveryAreaId: Int?,
    val estimatedDeliveryDate: String?,
    // "allow one to name a delivery ... a name delivery maasai allows
    // quick identification" - optional, purely cosmetic.
    val deliveryName: String? = null
)
