package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.OrphanFragmentRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrphanFragmentRuleTest {

    private val oneSecondNanos = 1_000_000_000L

    private fun stateWithOrphanCandidate(): RuntimeState {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(fragmentEvent(seq = 1, id = 2, stage = Stage.CREATED, hostId = 1))
        state = state.reduce(activityEvent(seq = 2, id = 1, stage = Stage.DESTROYED, nanos = 0))
        return state
    }

    @Test
    fun `fires when fragment is still alive one second after host died`() {
        val state = stateWithOrphanCandidate()

        val event = processEvent(
            seq = 3,
            state = RuntimeEvent.Process.State.BACKGROUNDED,
            nanos = oneSecondNanos + 100,
        )
        val risk = OrphanFragmentRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
        assertEquals("TestFragment", risk?.subject)
        assertEquals(2, risk?.instanceId)
    }

    @Test
    fun `stays silent while the grace period is still running`() {
        val state = stateWithOrphanCandidate()

        val event = processEvent(
            seq = 3,
            state = RuntimeEvent.Process.State.BACKGROUNDED,
            nanos = oneSecondNanos / 2,
        )
        val risk = OrphanFragmentRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the fragment died normally within the grace period`() {
        var state = stateWithOrphanCandidate()
        state = state.reduce(fragmentEvent(seq = 3, id = 2, stage = Stage.DESTROYED, hostId = 1, nanos = 100))

        val event = processEvent(
            seq = 4,
            state = RuntimeEvent.Process.State.BACKGROUNDED,
            nanos = oneSecondNanos * 2,
        )
        val risk = OrphanFragmentRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
