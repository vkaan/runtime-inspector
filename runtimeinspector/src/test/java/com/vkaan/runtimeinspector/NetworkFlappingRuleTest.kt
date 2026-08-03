package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.NetworkFlappingRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Network.State
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkFlappingRuleTest {

    private val rule = NetworkFlappingRule(lossCount = 3, windowSeconds = 60)

    private fun secs(s: Long): Long = s * 1_000_000_000L

    @Test
    fun `fires on the third loss inside the window`() {
        var state = RuntimeState()
        state = state.reduce(networkEvent(seq = 0, state = State.LOST, nanos = secs(0)))
        state = state.reduce(networkEvent(seq = 1, state = State.LOST, nanos = secs(20)))

        val event = networkEvent(seq = 2, state = State.LOST, nanos = secs(50))
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
        assertEquals("network", risk?.subject)
    }

    @Test
    fun `fires even when the network recovers between losses`() {
        // Loss-restore-loss cycling IS flapping; recoveries must not reset the count.
        var state = RuntimeState()
        state = state.reduce(networkEvent(seq = 0, state = State.LOST, nanos = secs(0)))
        state = state.reduce(networkEvent(seq = 1, state = State.AVAILABLE, nanos = secs(5)))
        state = state.reduce(networkEvent(seq = 2, state = State.LOST, nanos = secs(20)))
        state = state.reduce(networkEvent(seq = 3, state = State.AVAILABLE, nanos = secs(25)))

        val event = networkEvent(seq = 4, state = State.LOST, nanos = secs(50))
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when losses are spread out`() {
        var state = RuntimeState()
        state = state.reduce(networkEvent(seq = 0, state = State.LOST, nanos = secs(0)))
        state = state.reduce(networkEvent(seq = 1, state = State.LOST, nanos = secs(120)))

        val event = networkEvent(seq = 2, state = State.LOST, nanos = secs(240))
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the network comes back`() {
        var state = RuntimeState()
        state = state.reduce(networkEvent(seq = 0, state = State.LOST, nanos = secs(0)))
        state = state.reduce(networkEvent(seq = 1, state = State.LOST, nanos = secs(10)))

        val event = networkEvent(seq = 2, state = State.AVAILABLE, nanos = secs(20))
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
