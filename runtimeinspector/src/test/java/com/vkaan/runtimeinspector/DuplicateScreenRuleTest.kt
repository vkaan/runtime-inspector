package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.DuplicateScreenRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateScreenRuleTest {

    @Test
    fun `fires when a second live instance of the same activity appears`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = activityEvent(seq = 1, id = 2, stage = Stage.CREATED)
        val risk = DuplicateScreenRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when the new activity is a different screen`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))

        val event = activityEvent(seq = 1, id = 2, stage = Stage.CREATED, name = "OtherActivity")
        val risk = DuplicateScreenRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the previous instance was already destroyed`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.DESTROYED))

        val event = activityEvent(seq = 2, id = 2, stage = Stage.CREATED)
        val risk = DuplicateScreenRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
