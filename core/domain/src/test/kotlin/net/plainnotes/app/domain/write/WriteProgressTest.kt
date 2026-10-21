package net.plainnotes.app.domain.write

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WriteProgressTest {
    @Test fun unresolvedOutcomeNeverPermitsRetryOrPretendsToBeTerminal() {
        for(phase in listOf(WritePhase.QUEUED,WritePhase.WRITING,WritePhase.RECONCILING,WritePhase.MANUAL_CHECK)) {
            val view=WriteProgressSummary("SYNTHETIC operation",WriteStatus.PENDING,phase)
            assertFalse(view.retryAllowed)
            assertThrows(IllegalArgumentException::class.java){view.copy(retryAllowed=true)}
        }
        assertThrows(IllegalArgumentException::class.java){WriteProgressSummary("SYNTHETIC",WriteStatus.PENDING,WritePhase.TERMINAL)}
    }
    @Test fun successCannotOfferASecondSubmission() {
        val view=WriteProgressSummary("SYNTHETIC",WriteStatus.SUCCESS,WritePhase.TERMINAL)
        assertFalse(view.retryAllowed)
        assertThrows(IllegalArgumentException::class.java){view.copy(retryAllowed=true)}
        assertThrows(IllegalArgumentException::class.java){view.copy(phase=WritePhase.WRITING)}
    }
    @Test fun failureRequiresTerminalObservationAndRetryIsExplicit() {
        val view=WriteProgressSummary("SYNTHETIC",WriteStatus.FAILED,WritePhase.TERMINAL)
        assertFalse(view.retryAllowed)
        assertTrue(view.copy(retryAllowed=true).retryAllowed)
        assertThrows(IllegalArgumentException::class.java){view.copy(phase=WritePhase.RECONCILING)}
        assertThrows(IllegalArgumentException::class.java){view.copy(operationUuid=" ")}
    }
}
