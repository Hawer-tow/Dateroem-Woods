package com.dateroem.woodsales.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.companySimDataStore by preferencesDataStore(name = "dateroem_company_sim")

// "the reading will only happen on the phone with the company sim even if
// it has 2 sims it will read the company sim" (number 0700878260, this
// test phone) - a one-time pick of which SIM subscription is the company
// line, so the SMS listener can silently ignore the other one.
class CompanySimStore(private val context: Context) {

    private object Keys {
        val SUBSCRIPTION_ID = intPreferencesKey("company_sim_subscription_id")
        val LABEL = stringPreferencesKey("company_sim_label")
        val LAST_RESYNC_AT = longPreferencesKey("company_sim_last_resync_at")
    }

    val subscriptionId: Flow<Int?> = context.companySimDataStore.data.map { it[Keys.SUBSCRIPTION_ID] }
    val label: Flow<String?> = context.companySimDataStore.data.map { it[Keys.LABEL] }
    val lastResyncAt: Flow<Long?> = context.companySimDataStore.data.map { it[Keys.LAST_RESYNC_AT] }

    suspend fun subscriptionIdOnce(): Int? = subscriptionId.first()

    suspend fun setCompanySim(subscriptionId: Int, label: String) {
        context.companySimDataStore.edit { prefs ->
            prefs[Keys.SUBSCRIPTION_ID] = subscriptionId
            prefs[Keys.LABEL] = label
        }
    }

    suspend fun clear() {
        context.companySimDataStore.edit { it.clear() }
    }

    suspend fun markResynced() {
        context.companySimDataStore.edit { prefs -> prefs[Keys.LAST_RESYNC_AT] = System.currentTimeMillis() }
    }
}
