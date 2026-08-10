package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceCallBeforeBindRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardServiceCallBeforeBindRuleTest {

    private val rule = CardServiceCallBeforeBindRule

    @Test
    fun `fires when a call arrives before the service reports a bound client`() {
        val event = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.GET_CARD))
        val state = RuntimeState()

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("GET_CARD", risk?.subject)
    }

    @Test
    fun `stays silent once the bind line has arrived`() {
        val bind = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND))
        val bound = RuntimeState().reduce(bind)
        val event = cardServiceCall(seq = 1, apis = listOf(CardServiceApi.GET_CARD))

        val risk = rule.evaluate(event, before = bound, after = bound.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `the bind line itself is never a finding`() {
        val event = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND))
        val state = RuntimeState()

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
