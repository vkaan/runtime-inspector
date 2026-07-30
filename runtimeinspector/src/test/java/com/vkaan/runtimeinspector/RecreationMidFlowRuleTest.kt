package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.RecreationMidFlowRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecreationMidFlowRuleTest {

    @Test
    fun `fires when activity is destroyed by config change while flow is open`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 1))

        val event = activityEvent(seq = 2, id = 1, stage = Stage.DESTROYED, configChange = true)
        val risk = RecreationMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when destroy is not a config change`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 1))

        val event = activityEvent(seq = 2, id = 1, stage = Stage.DESTROYED)
        val risk = RecreationMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when back stack is empty`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = activityEvent(seq = 1, id = 1, stage = Stage.DESTROYED, configChange = true)
        val risk = RecreationMidFlowRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
