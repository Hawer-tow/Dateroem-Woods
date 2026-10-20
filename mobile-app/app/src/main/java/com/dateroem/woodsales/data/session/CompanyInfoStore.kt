package com.dateroem.woodsales.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.companyInfoDataStore by preferencesDataStore(name = "dateroem_company_info")

data class CompanyInfo(
    val name: String,
    val motto: String,
    val code: String
)

private val DEFAULT_COMPANY_INFO = CompanyInfo(
    name = "Dateroem Wood Sales",
    motto = "Quality timber, honest trade.",
    code = "DWS-001"
)

// Local-only, no backend table for this - "if none was selected then the
// default page will only give the information about the company and the
// company codes this you can create but i can adjust the motto etc." Kept
// on-device since it's cosmetic/admin-editable copy, not shared business data.
class CompanyInfoStore(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("company_name")
        val MOTTO = stringPreferencesKey("company_motto")
        val CODE = stringPreferencesKey("company_code")
    }

    val companyInfo: Flow<CompanyInfo> = context.companyInfoDataStore.data.map { prefs ->
        CompanyInfo(
            name = prefs[Keys.NAME] ?: DEFAULT_COMPANY_INFO.name,
            motto = prefs[Keys.MOTTO] ?: DEFAULT_COMPANY_INFO.motto,
            code = prefs[Keys.CODE] ?: DEFAULT_COMPANY_INFO.code
        )
    }

    suspend fun save(info: CompanyInfo) {
        context.companyInfoDataStore.edit { prefs ->
            prefs[Keys.NAME] = info.name
            prefs[Keys.MOTTO] = info.motto
            prefs[Keys.CODE] = info.code
        }
    }
}
