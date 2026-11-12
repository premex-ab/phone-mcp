package se.premex.mcp.remote

/** The next action is based on the live connection, even after a past success. */
enum class ActivationStep {
    CONNECT, PAIR, ENABLE_SENSOR, TRY_COMMAND, READY;

    companion object {
        fun from(connected: Boolean, paired: Boolean, successful: Boolean, sensorEnabled: Boolean) = when {
            !connected -> CONNECT
            !paired -> PAIR
            successful -> READY
            !sensorEnabled -> ENABLE_SENSOR
            else -> TRY_COMMAND
        }
    }
}

enum class TrialMessage { GET_STARTED, KEEP_USING }

fun RemoteEntitlement.trialMessage(now: Long = System.currentTimeMillis()): TrialMessage? =
    if (status == "trial" && daysLeft(now) in 1..7) {
        if (firstToolSuccess) TrialMessage.KEEP_USING else TrialMessage.GET_STARTED
    } else null
