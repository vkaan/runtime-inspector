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
        CardServiceLogPattern(Regex("waiting for card"), CardServiceState.WAITING_CARD),
        CardServiceLogPattern(Regex("card read"), CardServiceState.CARD_READ),
        CardServiceLogPattern(Regex("going online"), CardServiceState.ONLINE),
        CardServiceLogPattern(Regex("approved"), CardServiceState.APPROVED),
        CardServiceLogPattern(Regex("declined"), CardServiceState.DECLINED),
    )

    private fun tracker() = CardServiceLogState(patterns)

    private fun secs(s: Long): Long = s * 1_000_000_000L

    @Test
    fun `a matching line moves the state and reports the transition`() {
        val tracker = tracker()

        val transition = tracker.onLine("waiting for card", secs(1))

        assertEquals(CardServiceState.IDLE, transition?.from)
        assertEquals(CardServiceState.WAITING_CARD, transition?.to)
        assertEquals(CardServiceState.WAITING_CARD, tracker.state)
        assertEquals(secs(1), tracker.sinceNanos)
    }

    @Test
    fun `a line matching nothing leaves the state alone`() {
        val tracker = tracker()
        tracker.onLine("waiting for card", secs(1))

        val transition = tracker.onLine("battery level 82", secs(2))

        assertNull(transition)
        assertEquals(CardServiceState.WAITING_CARD, tracker.state)
        assertEquals(secs(1), tracker.sinceNanos)
    }

    @Test
    fun `repeating the current state does not re-fire and keeps the entry time`() {
        val tracker = tracker()
        tracker.onLine("waiting for card", secs(1))

        val transition = tracker.onLine("still waiting for card", secs(4))

        assertNull(transition)
        assertEquals(secs(1), tracker.sinceNanos)
        assertEquals("waiting for card", tracker.lastLine)
    }

    @Test
    fun `the first matching pattern wins`() {
        val ambiguous = listOf(
            CardServiceLogPattern(Regex("card"), CardServiceState.WAITING_CARD),
            CardServiceLogPattern(Regex("card read"), CardServiceState.CARD_READ),
        )

        val transition = CardServiceLogState(ambiguous).onLine("card read ok", secs(1))

        assertEquals(CardServiceState.WAITING_CARD, transition?.to)
    }

    @Test
    fun `the timeline state follows the transition`() {
        val event = cardServiceEvent(
            seq = 0,
            from = CardServiceState.IDLE,
            to = CardServiceState.ONLINE,
            nanos = secs(3),
        )

        val state = RuntimeState().reduce(event)

        assertEquals(CardServiceState.ONLINE, state.cardServiceState)
        assertEquals(secs(3), state.cardServiceSinceNanos)
        assertTrue(state.cardTransactionOpen)
    }

    @Test
    fun `a transaction is only open between the start and the result`() {
        var state = RuntimeState()
        assertFalse(state.cardTransactionOpen)

        state = state.reduce(
            cardServiceEvent(seq = 0, from = CardServiceState.IDLE, to = CardServiceState.CARD_READ)
        )
        assertTrue(state.cardTransactionOpen)

        state = state.reduce(
            cardServiceEvent(
                seq = 1,
                from = CardServiceState.CARD_READ,
                to = CardServiceState.DECLINED,
            )
        )
        assertFalse(state.cardTransactionOpen)
    }
}
