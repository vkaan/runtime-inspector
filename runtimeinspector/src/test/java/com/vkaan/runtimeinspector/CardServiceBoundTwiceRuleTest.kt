package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceBoundTwiceRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardServiceBoundTwiceRuleTest {

    private val rule = CardServiceBoundTwiceRule

    @Test
    fun `fires when a bind arrives while already bound with no unbind between`() {
        val first = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND))
        val bound = RuntimeState().reduce(first)
        val second = cardServiceCall(seq = 1, apis = listOf(CardServiceApi.BIND))

        val risk = rule.evaluate(second, before = bound, after = bound.reduce(second))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent on a normal bind - unbind - bind reconnect`() {
        var state = RuntimeState()
        state = state.reduce(cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND)))
        state = state.reduce(cardServiceCall(seq = 1, apis = listOf(CardServiceApi.UNBIND)))
        val rebind = cardServiceCall(seq = 2, apis = listOf(CardServiceApi.BIND))

        val risk = rule.evaluate(rebind, before = state, after = state.reduce(rebind))

        assertNull(risk)
    }

    @Test
    fun `the first bind is never a finding`() {
        val event = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.BIND))
        val state = RuntimeState()

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
