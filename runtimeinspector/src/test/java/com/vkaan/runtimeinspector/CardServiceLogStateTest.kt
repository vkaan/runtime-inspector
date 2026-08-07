package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceLogPattern
import com.vkaan.runtimeinspector.cardservice.CardServiceLogState
import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardServiceLogStateTest {

    private val patterns = listOf(
        CardServiceLogPattern(
            Regex("""getCard called with config:.*"emvProcessType"\s*:\s*1\b"""),
            CardServiceState.READ_CARD,
        ),
        CardServiceLogPattern(
            Regex("""getCard called with config:.*"emvProcessType"\s*:\s*2\b"""),
            CardServiceState.CONTINUE_EMV,
        ),
        CardServiceLogPattern(
            Regex("""getCard called with config:.*"emvProcessType"\s*:\s*3\b"""),
            CardServiceState.FULL_EMV,
        ),
        CardServiceLogPattern(
            Regex("completeEmv", RegexOption.IGNORE_CASE),
            CardServiceState.COMPLETED,
        ),
    )

    private val readCardLine =
        """getCard called with config: {"forceOnline":1,"emvProcessType":1,"keyIn":1}"""

    private val continueEmvLine =
        """getCard called with config: {"emvProcessType":2,"forceOnline":1,"keyIn":1}"""

    private fun tracker() = CardServiceLogState(patterns)

    private fun secs(s: Long): Long = s * 1_000_000_000L

    @Test
    fun `a matching line moves the state and reports the transition`() {
        val tracker = tracker()

        val transition = tracker.onLine(readCardLine, secs(1))

        assertEquals(CardServiceState.IDLE, transition?.from)
        assertEquals(CardServiceState.READ_CARD, transition?.to)
        assertEquals(CardServiceState.READ_CARD, tracker.state)
        assertEquals(secs(1), tracker.sinceNanos)
    }

    @Test
    fun `emvProcessType picks the state out of the config blob`() {
        val tracker = tracker()
        tracker.onLine(readCardLine, secs(1))

        val transition = tracker.onLine(continueEmvLine, secs(2))

        assertEquals(CardServiceState.CONTINUE_EMV, transition?.to)
    }

    @Test
    fun `completeEmvTxn closes the transaction whatever its casing`() {
        val tracker = tracker()
        tracker.onLine(continueEmvLine, secs(1))

        val transition = tracker.onLine("completeEMVTxn", secs(2))

        assertEquals(CardServiceState.COMPLETED, transition?.to)
    }

    @Test
    fun `a line matching nothing leaves the state alone`() {
        val tracker = tracker()
        tracker.onLine(readCardLine, secs(1))

        val transition = tracker.onLine("battery level 82", secs(2))

        assertNull(transition)
        assertEquals(CardServiceState.READ_CARD, tracker.state)
        assertEquals(secs(1), tracker.sinceNanos)
    }

    @Test
    fun `repeating the current state does not re-fire and keeps the entry time`() {
        val tracker = tracker()
        tracker.onLine(readCardLine, secs(1))

        val transition = tracker.onLine(
            """getCard called with config: {"emvProcessType":1,"zeroAmount":0}""",
            secs(4),
        )

        assertNull(transition)
        assertEquals(secs(1), tracker.sinceNanos)
        assertEquals(readCardLine, tracker.lastLine)
    }

    @Test
    fun `the first matching pattern wins`() {
        val ambiguous = listOf(
            CardServiceLogPattern(Regex("getCard"), CardServiceState.READ_CARD),
            CardServiceLogPattern(Regex("""emvProcessType"\s*:\s*2"""), CardServiceState.CONTINUE_EMV),
        )

        val transition = CardServiceLogState(ambiguous).onLine(continueEmvLine, secs(1))

        assertEquals(CardServiceState.READ_CARD, transition?.to)
    }

    @Test
    fun `the timeline state follows the transition`() {
        val event = cardServiceEvent(
            seq = 0,
            from = CardServiceState.IDLE,
            to = CardServiceState.CONTINUE_EMV,
            nanos = secs(3),
        )

        val state = RuntimeState().reduce(event)

        assertEquals(CardServiceState.CONTINUE_EMV, state.cardServiceState)
        assertEquals(secs(3), state.cardServiceSinceNanos)
        assertTrue(state.cardTransactionOpen)
    }

    @Test
    fun `a card read on its own is not an open transaction`() {
        val state = RuntimeState().reduce(
            cardServiceEvent(seq = 0, from = CardServiceState.IDLE, to = CardServiceState.READ_CARD)
        )

        assertFalse(state.cardTransactionOpen)
    }

    @Test
    fun `a transaction is only open until completeEmvTxn arrives`() {
        var state = RuntimeState()
        assertFalse(state.cardTransactionOpen)

        state = state.reduce(
            cardServiceEvent(
                seq = 0,
                from = CardServiceState.READ_CARD,
                to = CardServiceState.CONTINUE_EMV,
            )
        )
        assertTrue(state.cardTransactionOpen)

        state = state.reduce(
            cardServiceEvent(
                seq = 1,
                from = CardServiceState.CONTINUE_EMV,
                to = CardServiceState.COMPLETED,
            )
        )
        assertFalse(state.cardTransactionOpen)
    }
}
