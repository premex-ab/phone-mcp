package se.premex.mcp.remote

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

data class RemoteEntitlement(val status: String, val activeUntil: String, val firstToolSuccess: Boolean = false, val acknowledged: Boolean = false) {
    /** The API period excludes our seven-day grace. Round up partial days. API 24 safe. */
    fun daysLeft(now: Long = System.currentTimeMillis()): Long = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val end = requireNotNull(format.parse(activeUntil.substringBefore('.').removeSuffix("Z"))).time +
            if (status == "grace") 7 * 86_400_000L else 0L
        ceil((end - now).toDouble() / 86_400_000).toLong().coerceAtLeast(0)
    }.getOrDefault(0)
}
