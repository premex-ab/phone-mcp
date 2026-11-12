package se.premex.mcp.remote

import org.junit.Assert.*
import org.junit.Test

class ActivationProgressTest {
    @Test fun `a past success does not hide a broken connection or revoked pairing`() {
        assertEquals(ActivationStep.CONNECT, ActivationStep.from(false, true, true, true))
        assertEquals(ActivationStep.PAIR, ActivationStep.from(true, false, true, true))
    }

    @Test fun `new users enable the test tool before they are asked to run it`() {
        assertEquals(ActivationStep.ENABLE_SENSOR, ActivationStep.from(true, true, false, false))
        assertEquals(ActivationStep.TRY_COMMAND, ActivationStep.from(true, true, false, true))
    }

    @Test fun `successful users can move on without keeping the sensor enabled`() {
        assertEquals(ActivationStep.READY, ActivationStep.from(true, true, true, false))
    }

    @Test fun `trial copy is based on demonstrated use and only the final seven days`() {
        val trial = RemoteEntitlement("trial", "1970-01-08T00:00:00Z")
        assertEquals(TrialMessage.GET_STARTED, trial.trialMessage(0))
        assertEquals(TrialMessage.KEEP_USING, trial.copy(firstToolSuccess = true).trialMessage(0))
        assertNull(trial.copy(activeUntil = "1970-01-09T00:00:00Z").trialMessage(0))
    }

    @Test fun `expired paid and invalid periods do not receive trial reminders`() {
        assertNull(RemoteEntitlement("trial", "invalid").trialMessage(0))
        assertNull(RemoteEntitlement("trial", "1970-01-01T00:00:00Z").trialMessage(0))
        assertNull(RemoteEntitlement("paid", "1970-01-02T00:00:00Z").trialMessage(0))
        assertNull(RemoteEntitlement("grace", "1970-01-01T00:00:00Z").trialMessage(0))
    }
}
