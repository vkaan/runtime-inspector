package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.rules.CardReadBeforeEmvConfigRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardReadBeforeEmvConfigRuleTest {

    private val rule = CardReadBeforeEmvConfigRule

    private val bound = RuntimeState().reduce(cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND)))

    @Test
    fun `fires when a bound client reads a card before any EMV config is set`() {
        val event = cardServiceCall(seq = 1, apis = listOf(CardServiceApi.GET_CARD))

        val risk = rule.evaluate(event, before = bound, after = bound.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("GET_CARD", risk?.subject)
    }

    @Test
    fun `fires when only the contact config was set and the contactless one is missing`() {
        val contactOnly = bound.reduce(cardServiceCall(seq = 1, apis = listOf(CardServiceApi.SET_EMV_CONFIG)))
        val event = cardServiceCall(seq = 2, apis = listOf(CardServiceApi.GET_CARD))

        val risk = rule.evaluate(event, before = contactOnly, after = contactOnly.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
    }

    @Test
    fun `stays silent once both configs have run`() {
        val configured = bound
            .reduce(cardServiceCall(seq = 1, apis = listOf(CardServiceApi.SET_EMV_CONFIG)))
            .reduce(cardServiceCall(seq = 2, apis = listOf(CardServiceApi.SET_EMV_CL_CONFIG)))
        val event = cardServiceCall(seq = 3, apis = listOf(CardServiceApi.GET_CARD))

        val risk = rule.evaluate(event, before = configured, after = configured.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `leaves an unbound getCard to CardServiceCallBeforeBindRule`() {
        val event = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.GET_CARD))
        val state = RuntimeState()

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
