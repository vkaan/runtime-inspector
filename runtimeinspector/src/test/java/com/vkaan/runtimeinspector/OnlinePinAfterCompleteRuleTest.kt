package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.cardservice.rules.OnlinePinAfterCompleteRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlinePinAfterCompleteRuleTest {

    private val rule = OnlinePinAfterCompleteRule

    private val completed: RuntimeState = RuntimeState().reduce(
        cardServiceEvent(seq = 0, from = CardServiceState.CONTINUE_EMV, to = CardServiceState.COMPLETED)
    )

    @Test
    fun `fires on a standalone PIN request after the transaction completed`() {
        val event = cardServiceCall(
            seq = 1,
            apis = listOf(CardServiceApi.GET_ONLINE_PIN),
            state = CardServiceState.COMPLETED,
        )

        val risk = rule.evaluate(event, before = completed, after = completed.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("GET_ONLINE_PIN", risk?.subject)
    }

    @Test
    fun `stays silent when the PIN rides on a getCard that starts a new transaction`() {
        val event = cardServiceCall(
            seq = 1,
            apis = listOf(CardServiceApi.GET_CARD, CardServiceApi.GET_ONLINE_PIN),
            state = CardServiceState.COMPLETED,
            to = CardServiceState.READ_CARD,
        )

        val risk = rule.evaluate(event, before = completed, after = completed.reduce(event))

        assertNull(risk)
    }
}
