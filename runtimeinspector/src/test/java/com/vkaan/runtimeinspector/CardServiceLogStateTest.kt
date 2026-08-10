package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
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
            CardServiceApi.GET_CARD,
            CardServiceState.READ_CARD,
        ),
        CardServiceLogPattern(
            Regex("""getCard called with config:.*"emvProcessType"\s*:\s*2\b"""),
            CardServiceApi.GET_CARD,
            CardServiceState.CONTINUE_EMV,
        ),
        CardServiceLogPattern(
            Regex("""getCard called with config:.*"emvProcessType"\s*:\s*3\b"""),
            CardServiceApi.GET_CARD,
            CardServiceState.FULL_EMV,
        ),
        CardServiceLogPattern(
            Regex("getOnlinePIN"),
            CardServiceApi.GET_ONLINE_PIN,
        ),
        CardServiceLogPattern(
            Regex("completeEmv", RegexOption.IGNORE_CASE),
            CardServiceApi.COMPLETE_EMV,
            CardServiceState.COMPLETED,
        ),
        CardServiceLogPattern(
            Regex("A client is bound"),
            CardServiceApi.BIND,
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
    fun `a repeated call is still reported but keeps the entry time`() {
        val tracker = tracker()
        tracker.onLine(readCardLine, secs(1))
        val repeated = """getCard called with config: {"emvProcessType":1,"zeroAmount":0}"""

        val match = tracker.onLine(repeated, secs(4))

        assertEquals(listOf(CardServiceApi.GET_CARD), match?.apis)
        assertEquals(CardServiceState.READ_CARD, match?.from)
        assertEquals(CardServiceState.READ_CARD, match?.to)
        assertEquals(secs(1), tracker.sinceNanos)
        assertEquals(repeated, tracker.lastLine)
    }

    @Test
    fun `a line carrying two APIs reports both`() {
        val line =
            """getCard called with config: {"emvProcessType":2,"getOnlinePIN":1}"""

        val match = tracker().onLine(line, secs(1))

        assertEquals(
            listOf(CardServiceApi.GET_CARD, CardServiceApi.GET_ONLINE_PIN),
            match?.apis,
        )
        assertEquals(CardServiceState.CONTINUE_EMV, match?.to)
    }

    @Test
    fun `a matched line with no state leaves the state alone`() {
        val tracker = tracker()

        val match = tracker.onLine("A client is bound", secs(1))

        assertEquals(listOf(CardServiceApi.BIND), match?.apis)
        assertEquals(CardServiceState.IDLE, tracker.state)
        assertEquals(0L, tracker.sinceNanos)
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
