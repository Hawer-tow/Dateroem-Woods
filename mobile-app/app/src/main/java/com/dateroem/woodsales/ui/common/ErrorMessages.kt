package com.dateroem.woodsales.ui.common

import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * "in the event of an error e.g the server is offline ... the user in the
 * website or mobile never gets the whole details but what is necessary and
 * presentable" (18 October 2026). One place that turns any failure into a
 * short, plain sentence for the screen:
 *
 * - the server's own reason, when it wrote one for people (a 4xx "detail"
 *   such as "Admin/SuperAdmin only" or "Not enough stock");
 * - otherwise a calm message by kind - offline, slow, a problem on the
 *   server - with no addresses, exception names or HTTP codes;
 * - [fallback], the screen's own words, for anything else.
 *
 * The full error still goes to the server logs; the phone shows only this.
 */
fun friendlyError(e: Throwable, fallback: String): String =
    friendlyError(e, fallback, if (e is HttpException) runCatching { e.response()?.errorBody()?.string() }.getOrNull() else null)

/** The same, for an error whose body the caller has already read (it can be read only once). */
fun friendlyError(e: Throwable, fallback: String, body: String?): String {
    when (e) {
        is HttpException -> {
            val code = e.code()
            if (code in 400..499) {
                val detail = Regex("\"(?:detail|message)\"\\s*:\\s*\"([^\"]+)\"").find(body.orEmpty())?.groupValues?.get(1)
                if (!detail.isNullOrBlank()) return detail
            }
            return when (code) {
                401 -> "Your sign-in has ended. Please sign in again."
                403 -> "You don't have access to this."
                404 -> "This item is no longer available."
                408, 504 -> "The server took too long to answer. Please try again."
                413 -> "That file is too large to send."
                429 -> "Too many tries. Please wait a moment and try again."
                502, 503 -> "The Dateroem server is offline for a moment. Please try again in a few minutes."
                in 500..599 -> "Something went wrong on our side. Please try again in a moment."
                else -> fallback
            }
        }
        // Timeouts first: they are IOExceptions too.
        is InterruptedIOException -> return "The server took too long to answer. Check your connection and try again."
        is IOException -> return "Can't reach the Dateroem server. Check your internet connection and try again."
    }
    return fallback
}
