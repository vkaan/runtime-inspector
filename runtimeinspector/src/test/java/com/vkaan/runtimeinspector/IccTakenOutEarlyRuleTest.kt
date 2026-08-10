package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.cardservice.rules.IccTakenOutEarlyRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IccTakenOutEarlyRuleTest {

    private val rule = IccTakenOutEarlyRule

    private fun stateIn(state: CardServiceState): RuntimeState =
        RuntimeState().reduce(
            cardServiceEvent(seq = 0, from = CardServiceState.IDLE, to = state)
        )

    @Test
    fun `fires when the card is released while EMV is still open`() {
        val open = stateIn(CardServiceState.CONTINUE_EMV)
        val event = cardServiceCall(
            seq = 1,
            apis = listOf(CardServiceApi.TAKE_OUT_ICC),
            state = CardServiceState.CONTINUE_EMV,
        )

        val risk = rule.evaluate(event, before = open, after = open.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("CONTINUE_EMV", risk?.subject)
    }

    @Test
    fun `stays silent once the transaction is completed`() {
        val done = stateIn(CardServiceState.COMPLETED)
        val event = cardServiceCall(
            seq = 1,
            apis = listOf(CardServiceApi.TAKE_OUT_ICC),
            state = CardServiceState.COMPLETED,
        )

        val risk = rule.evaluate(event, before = done, after = done.reduce(event))

        assertNull(risk)
    }
}
