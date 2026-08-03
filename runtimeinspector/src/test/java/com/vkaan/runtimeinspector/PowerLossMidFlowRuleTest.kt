package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.PowerLossMidFlowRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.SystemSignal.Signal
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerLossMidFlowRuleTest {

    private fun midFlowState(): RuntimeState {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 2, id = 1, stage = Stage.RESUMED, backStackCount = 1))
        return state
    }

    @Test
    fun `fires as WARNING on battery low mid-flow`() {
        val state = midFlowState()

        val event = systemEvent(seq = 3, signal = Signal.BATTERY_LOW)
        val risk = PowerLossMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `fires as ERROR on shutdown mid-flow`() {
        val state = midFlowState()

        val event = systemEvent(seq = 3, signal = Signal.SHUTDOWN)
        val risk = PowerLossMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
    }

    @Test
    fun `stays silent when no flow is open`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 0))

        val event = systemEvent(seq = 2, signal = Signal.BATTERY_LOW)
        val risk = PowerLossMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the battery recovers`() {
        val state = midFlowState()

        val event = systemEvent(seq = 3, signal = Signal.BATTERY_OKAY)
        val risk = PowerLossMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
