package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.NetworkLossRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkLossRuleTest {

    @Test
    fun `fires as WARNING when network is lost mid-flow`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 2, id = 1, stage = Stage.RESUMED, backStackCount = 1))

        val event = networkEvent(seq = 3, state = RuntimeEvent.Network.State.LOST)
        val risk = NetworkLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `fires as INFO when network is lost with no flow open`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.CREATED))

        val event = networkEvent(seq = 2, state = RuntimeEvent.Network.State.LOST)
        val risk = NetworkLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.INFO, risk?.severity)
    }

    @Test
    fun `stays silent when the app is backgrounded`() {
        val state = RuntimeState()

        val event = networkEvent(seq = 0, state = RuntimeEvent.Network.State.LOST)
        val risk = NetworkLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the network comes back`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))

        val event = networkEvent(seq = 1, state = RuntimeEvent.Network.State.AVAILABLE)
        val risk = NetworkLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
