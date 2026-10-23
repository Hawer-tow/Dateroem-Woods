package com.dateroem.woodsales.ui.orders

import androidx.compose.ui.graphics.Color
import com.dateroem.woodsales.data.network.JsonMap

// The orders endpoints return plain JSON (Gson turns objects into maps and
// arrays into lists) - these keep the reading code below short and safe.

@Suppress("UNCHECKED_CAST")
fun JsonMap.maps(key: String): List<JsonMap> = (this[key] as? List<*>)?.mapNotNull { it as? JsonMap } ?: emptyList()

fun JsonMap.int(key: String): Int? = (this[key] as? Number)?.toInt()

fun JsonMap.text(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

fun JsonMap.flag(key: String): Boolean = this[key] == true || (this[key] as? Number)?.toInt() == 1

fun JsonMap.money(key: String): String {
    val n = (this[key] as? Number)?.toDouble() ?: return "0"
    return if (n % 1.0 == 0.0) n.toLong().toString() else "%.2f".format(n)
}

// The order's ticket (DW-7K3Q9P, migration 038) - what the customer quotes -
// or its number for an order from before tickets existed.
fun JsonMap.ticket(): String = text("ticketCode") ?: "#${int("orderId")}"

fun JsonMap.feet(key: String = "bodyFeet"): String {
    val n = (this[key] as? Number)?.toDouble() ?: return "-"
    return "%,.0f".format(n)
}

// Only dates with a real value: the API sends "2026-09-28" or null.
fun JsonMap.date(key: String): String? = (this[key] as? String)?.take(10)?.takeIf { it.isNotBlank() }

// "if an order has not been assigned yet and the time is near let it be
// treated as a risky product ... indicated differently" (24 October 2026):
// the backend marks a waiting order DUE_SOON close to the date the customer
// was promised and LATE once it has passed (DeliveryDeadline).
val LateRed = Color(0xFFE53935)
val DueSoonOrange = Color(0xFFF57C00)

fun deadlineColor(risk: String?): Color? = when (risk) {
    "LATE" -> LateRed
    "DUE_SOON" -> DueSoonOrange
    else -> null
}

/** "2 days late", "Due today", "3 days left" - null for an order with time to spare. */
fun deadlineText(risk: String?, daysLeft: Int?): String? {
    if (risk == null || daysLeft == null) return null
    return when {
        risk == "LATE" -> "LATE · ${-daysLeft} day${if (-daysLeft != 1) "s" else ""} past the promised date"
        daysLeft == 0 -> "DUE TODAY"
        else -> "DUE SOON · $daysLeft day${if (daysLeft != 1) "s" else ""} left"
    }
}

fun batchLabel(batch: JsonMap): String {
    val word = if (batch.text("kind") == "bulk") "Bulk" else "Light"
    val parts = listOfNotNull(
        "$word delivery #${batch.int("targetId")}",
        "trip #${batch.int("tripId")}",
        batch.date("date"),
        batch.text("vehicle"),
        if (batch.flag("shareDriver")) "driver shared" else null
    )
    return parts.joinToString(" · ")
}
