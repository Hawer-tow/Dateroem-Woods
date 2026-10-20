package com.dateroem.woodsales.data.network

import com.dateroem.woodsales.BuildConfig
import com.dateroem.woodsales.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// mobileServer runs on the dev machine at port 8001. Two ways a device
// can reach it during development, and they use different addresses:
//   - Emulator: 10.0.2.2 is its built-in alias for the host's localhost.
//   - Physical device over USB: 127.0.0.1 here, paired with
//     `adb reverse tcp:8001 tcp:8001` on the host, which tunnels the
//     phone's own port 8001 back to the dev machine over the USB
//     connection - no Wi-Fi/LAN/firewall involved at all.
// Trying to reach mobileServer over real Wi-Fi/LAN instead hit WSL2
// mirrored-mode's port sharing not extending to inbound LAN connections
// from other devices, so adb reverse is the reliable path for now.
// Now a build-config value (see app/build.gradle.kts) - defaults to the
// USB/adb-reverse address above, or pass -PmobileBaseUrl=<tunnel URL> for
// a build someone without USB access to this machine can install.
private val MOBILE_SERVER_BASE_URL = BuildConfig.MOBILE_SERVER_BASE_URL

private class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionManager.tokenOnce() }
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}

// "only one user per account per mobile phone" - the server answers 401 when
// this phone's sign-in is no longer valid (the phone was revoked or replaced
// by an admin, the account was disabled, or the sign-in is from an older
// app version without a phone key). The session is ended right there, so
// the app goes back to the sign-in screen with the reason, instead of every
// screen showing its own error.
private class SessionGuardInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val path = request.url.encodedPath
        val signingIn = path.endsWith("auth/login") || path.endsWith("auth/device/challenge")
        if (response.code == 401 && !signingIn && request.header("Authorization") != null) {
            val body = response.peekBody(4096).string()
            val reason = if (body.contains("DEVICE_REVOKED")) {
                "This phone is no longer approved for your account. Ask the SuperAdmin to approve it, then sign in again."
            } else {
                "Please sign in again."
            }
            runBlocking { sessionManager.endSession(reason) }
        }
        return response
    }
}

object RetrofitClient {
    @Volatile
    private var api: ApiService? = null

    fun get(sessionManager: SessionManager): ApiService {
        return api ?: synchronized(this) {
            api ?: build(sessionManager).also { api = it }
        }
    }

    private fun build(sessionManager: SessionManager): ApiService {
        // BODY level while diagnosing the create-product 400 - reveals the
        // exact request/response JSON in logcat instead of just the status
        // line. Dial back to BASIC once that's understood.
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(SessionGuardInterceptor(sessionManager))
            .addInterceptor(logging)
            // OkHttp's default is 10s each - fine for plain CRUD, but the
            // AI chat/scan calls go phone -> mobileServer -> backend ->
            // Anthropic and back, and an LLM call itself can take several
            // seconds even for a short prompt ("having a timeout ... for
            // the prompt morning" - a trivial message still needs the
            // full round trip). mobileServer's own proxy already waits up
            // to 45s for those two endpoints; this just needs to be at
            // least that generous, or the phone gives up first.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(MOBILE_SERVER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
