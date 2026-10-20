package com.dateroem.woodsales.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// "since this app is in the online world sometimes offline is it possible
// to allow the coordinate section to be filled automatically by the
// system checking current coordinates" - GPS itself needs no network at
// all, so this works fully offline; briefly waits for a fresh fix, then
// falls back to whatever's cached (still useful under tree cover or a
// weak signal, better than leaving the field empty).
suspend fun currentLocationOrNull(context: Context, timeoutMs: Long = 8000): Location? {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val fresh = withTimeoutOrNull(timeoutMs) { requestFreshFix(locationManager) }
    return fresh ?: bestLastKnown(locationManager)
}

fun Location.toCoordinateString(): String = "%.6f,%.6f".format(latitude, longitude)

// "allow it to collect coordinates online if online and offline if
// possible" - GPS_PROVIDER needs no connectivity at all, NETWORK_PROVIDER
// is quicker (cell/Wi-Fi based) when there is one; rather than guessing
// which situation this is, both enabled providers are started at once and
// whichever reports a fix first wins - network wins the race when online,
// GPS is what's left when it isn't.
private suspend fun requestFreshFix(locationManager: LocationManager): Location? = suspendCancellableCoroutine { cont ->
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { locationManager.isProviderEnabled(it) }

    if (providers.isEmpty()) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationManager.removeUpdates(this)
            if (cont.isActive) cont.resume(location)
        }
    }

    try {
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }
    } catch (e: SecurityException) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
}

private fun bestLastKnown(locationManager: LocationManager): Location? {
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    return providers.mapNotNull {
        try {
            locationManager.getLastKnownLocation(it)
        } catch (e: SecurityException) {
            null
        }
    }.maxByOrNull { it.time }
}
