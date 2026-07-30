package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.BackStackGrowthRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackStackGrowthRuleTest {

    private val rule = BackStackGrowthRule(ceiling = 3)

    @Test
    fun `fires when the back stack reaches the ceiling`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 3)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
        assertEquals("TestActivity#1", risk?.subject)
    }

    @Test
    fun `stays silent below the ceiling`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = activityEvent(seq = 1, id = 1, stage = Stage.RESUMED, backStackCount = 2)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
