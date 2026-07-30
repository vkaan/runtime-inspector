package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.InterruptedFlowRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterruptedFlowRuleTest {

    @Test
    fun `fires as INFO when app is backgrounded while a flow is open`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 2))

        val event = processEvent(seq = 2, state = RuntimeEvent.Process.State.BACKGROUNDED)
        val risk = InterruptedFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.INFO, risk?.severity)
    }

    @Test
    fun `stays silent when no flow is open`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = processEvent(seq = 1, state = RuntimeEvent.Process.State.BACKGROUNDED)
        val risk = InterruptedFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
