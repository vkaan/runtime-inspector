package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.ScreenOffMidFlowRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.SystemSignal.Signal
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenOffMidFlowRuleTest {

    private fun midFlowState(): RuntimeState {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 2, id = 1, stage = Stage.RESUMED, backStackCount = 1))
        return state
    }

    @Test
    fun `fires when the screen turns off mid-flow`() {
        val state = midFlowState()

        val event = systemEvent(seq = 3, signal = Signal.SCREEN_OFF)
        val risk = ScreenOffMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when no flow is open`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 0))

        val event = systemEvent(seq = 2, signal = Signal.SCREEN_OFF)
        val risk = ScreenOffMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the app is backgrounded`() {
        var state = midFlowState()
        state = state.reduce(processEvent(seq = 3, state = RuntimeEvent.Process.State.BACKGROUNDED))

        val event = systemEvent(seq = 4, signal = Signal.SCREEN_OFF)
        val risk = ScreenOffMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the screen turns on`() {
        val state = midFlowState()

        val event = systemEvent(seq = 3, signal = Signal.SCREEN_ON)
        val risk = ScreenOffMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
