package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.MidFlowCrashRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

class MidFlowCrashRuleTest {

    @Test
    fun `fires as ERROR when the app crashes mid-flow`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 1))

        val event = crashEvent(seq = 2)
        val risk = MidFlowCrashRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
    }

    @Test
    fun `fires as WARNING when the app crashes with no flow open`() {
        val state = RuntimeState()

        val event = crashEvent(seq = 0)
        val risk = MidFlowCrashRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }
}
