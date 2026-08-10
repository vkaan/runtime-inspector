package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.cardservice.rules.TransactionInterruptedRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.SystemSignal.Signal
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionInterruptedRuleTest {

    private val rule = TransactionInterruptedRule

    private fun openTransaction(to: CardServiceState = CardServiceState.CONTINUE_EMV): RuntimeState =
        RuntimeState().reduce(cardServiceEvent(seq = 0, from = CardServiceState.IDLE, to = to))

    @Test
    fun `fires when the screen turns off mid-transaction`() {
        val state = openTransaction()
        val event = systemEvent(seq = 1, signal = Signal.SCREEN_OFF)

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
        assertEquals("CONTINUE_EMV:screen-off", risk?.subject)
    }

    @Test
    fun `a crash mid-transaction is an error, not a warning`() {
        val state = openTransaction(CardServiceState.FULL_EMV)
        val event = crashEvent(seq = 1)

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("FULL_EMV:crash", risk?.subject)
    }

    @Test
    fun `fires when the Activity is recreated mid-transaction`() {
        val state = openTransaction()
        val event = activityEvent(seq = 1, id = 1, stage = Stage.DESTROYED, configChange = true)

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals("CONTINUE_EMV:recreation", risk?.subject)
    }

    @Test
    fun `stays silent when the transaction has already finished`() {
        var state = openTransaction()
        state = state.reduce(
            cardServiceEvent(
                seq = 1,
                from = CardServiceState.CONTINUE_EMV,
                to = CardServiceState.COMPLETED,
            )
        )
        val event = systemEvent(seq = 2, signal = Signal.SCREEN_OFF)

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `an ordinary destroy is not an interruption`() {
        val state = openTransaction()
        val event = activityEvent(seq = 1, id = 1, stage = Stage.DESTROYED, configChange = false)

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
