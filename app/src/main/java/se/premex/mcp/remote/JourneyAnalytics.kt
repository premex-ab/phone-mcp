package se.premex.mcp.remote

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import se.premex.mcp.BuildConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class JourneyEvent(val eventName: String) {
    REGISTERED("remote_registered"),
    CONNECTED("remote_connected"),
    PAIRED("remote_paired"),
    TOOL_SUCCESS("remote_tool_success"),
    SETUP_STEP("remote_setup_step"),
    CLIENT_SELECTED("remote_client_selected"),
    GUIDE_OPENED("remote_guide_opened"),
    PROMPT_COPIED("remote_prompt_copied"),
    PAYWALL_VIEWED("remote_paywall_viewed"),
    CHECKOUT_STARTED("remote_checkout_started"),
    CHECKOUT_CANCELLED("remote_checkout_cancelled"),
    CHECKOUT_FAILED("remote_checkout_failed"),
    ENTITLEMENT_ACTIVE("remote_entitlement_active"),
    TRIAL_HELP_VIEWED("remote_trial_help_viewed"),
    TRIAL_REMINDER_VIEWED("remote_trial_reminder_viewed"),
}

/** Coarse product actions only. Never accepts tokens, prompts, tool results or identifiers. */
@Singleton
class JourneyAnalytics @Inject constructor(@ApplicationContext private val context: Context) {
    private val observed by lazy { context.getSharedPreferences("journey_observations", Context.MODE_PRIVATE) }

    fun record(event: JourneyEvent, client: String? = null, step: ActivationStep? = null, plan: String? = null): Boolean {
        // Full/F-Droid and builds without google-services.json must remain inert.
        if (!BuildConfig.REMOTE_ACCESS || FirebaseApp.getApps(context).isEmpty()) return false
        return runCatching {
            val parameters = Bundle().apply {
                client?.lowercase(Locale.ROOT)?.takeIf { it in CLIENTS }?.let { putString("ai_client", it) }
                step?.let { putString("setup_step", it.name.lowercase(Locale.ROOT)) }
                plan?.takeIf { it == "monthly" || it == "yearly" }?.let { putString("base_plan", it) }
            }
            FirebaseAnalytics.getInstance(context).logEvent(event.eventName, parameters)
        }.isSuccess
    }

    /** A state may predate this app update. These are observations, not dated transactions. */
    @Synchronized
    fun observedOnce(event: JourneyEvent) {
        if (!observed.getBoolean(event.eventName, false) && record(event)) {
            observed.edit().putBoolean(event.eventName, true).apply()
        }
    }

    companion object {
        private val CLIENTS = setOf("claude_code", "claude_desktop", "gemini_cli", "codex", "other")
    }
}
