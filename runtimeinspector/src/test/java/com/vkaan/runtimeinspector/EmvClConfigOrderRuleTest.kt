package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.rules.EmvClConfigOrderRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmvClConfigOrderRuleTest {

    private val rule = EmvClConfigOrderRule

    @Test
    fun `fires when contactless is configured first`() {
        val event = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.SET_EMV_CL_CONFIG))
        val state = RuntimeState()

        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("SET_EMV_CL_CONFIG", risk?.subject)
    }

    @Test
    fun `stays silent when contact EMV was configured first`() {
        val contact = cardServiceCall(seq = 0, apis = listOf(CardServiceApi.SET_EMV_CONFIG))
        val configured = RuntimeState().reduce(contact)
        val event = cardServiceCall(seq = 1, apis = listOf(CardServiceApi.SET_EMV_CL_CONFIG))

        val risk = rule.evaluate(event, before = configured, after = configured.reduce(event))

        assertNull(risk)
    }
}
