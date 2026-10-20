package com.dateroem.woodsales.ui.orders

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

fun JsonMap.feet(key: String = "bodyFeet"): String {
    val n = (this[key] as? Number)?.toDouble() ?: return "-"
    return "%,.0f".format(n)
}

// Only dates with a real value: the API sends "2026-09-28" or null.
fun JsonMap.date(key: String): String? = (this[key] as? String)?.take(10)?.takeIf { it.isNotBlank() }

fun batchLabel(batch: JsonMap): String {
    val word = if (batch.text("kind") == "bulk") "Bulk" else "Light"
    val parts = listOfNotNull(
        "$word delivery #${batch.int("targetId")}",
        "trip #${batch.int("tripId")}",
        batch.date("date"),
        batch.text("vehicle")
    )
    return parts.joinToString(" · ")
}
