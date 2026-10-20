package com.dateroem.woodsales

import android.content.Context
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.RetrofitClient
import com.dateroem.woodsales.data.security.DeviceIdentity
import com.dateroem.woodsales.data.session.ActiveTripStore
import com.dateroem.woodsales.data.session.CompanyInfoStore
import com.dateroem.woodsales.data.session.CompanySimStore
import com.dateroem.woodsales.data.session.SessionManager

// Manual, minimal DI: fine at this stage given the app's current size.
// Worth swapping for Hilt once more screens/viewmodels make the manual
// wiring here noisy.
class AppContainer(context: Context) {
    val sessionManager = SessionManager(context.applicationContext)
    val activeTripStore = ActiveTripStore(context.applicationContext)
    val companyInfoStore = CompanyInfoStore(context.applicationContext)
    // "the reading will only happen on the phone with the company sim" -
    // which subscription that is, picked once (see CompanySimSetupScreen).
    val companySimStore = CompanySimStore(context.applicationContext)
    val api: ApiService by lazy { RetrofitClient.get(sessionManager) }
    // This install's own key - proves which phone is signing in.
    val deviceIdentity: DeviceIdentity by lazy { DeviceIdentity() }
}
