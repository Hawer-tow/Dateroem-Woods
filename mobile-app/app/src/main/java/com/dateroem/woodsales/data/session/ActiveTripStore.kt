package com.dateroem.woodsales.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activeTripDataStore by preferencesDataStore(name = "dateroem_active_trip")

// "when one chooses trip number 2 from the rotation then the default
// layout data is updated to match this as well as any other section...
// if none was selected ... if you get into a section expenses you will
// have to select the trip since none has been selected from the short
// cut" - a single persisted, app-wide "which trip am I working on right
// now" pointer that the dashboard and every trip-scoped section (Expenses,
// Bulk Orders, Loans & Finance) read from before falling back to asking.
class ActiveTripStore(private val context: Context) {

    private object Keys {
        val ACTIVE_TRIP_ID = intPreferencesKey("active_trip_id")
    }

    val activeTripId: Flow<Int?> = context.activeTripDataStore.data.map { prefs -> prefs[Keys.ACTIVE_TRIP_ID] }

    suspend fun setActiveTrip(tripId: Int?) {
        context.activeTripDataStore.edit { prefs ->
            if (tripId == null) prefs.remove(Keys.ACTIVE_TRIP_ID) else prefs[Keys.ACTIVE_TRIP_ID] = tripId
        }
    }
}
