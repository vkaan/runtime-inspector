package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.StateLossRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateLossRuleTest {

    @Test
    fun `fires when fragment is created on a stopped host`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.STOPPED))

        val event = fragmentEvent(seq = 2, id = 2, stage = Stage.CREATED, hostId = 1)
        val risk = StateLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
    }

    @Test
    fun `stays silent when host is resumed`() {
        var state = RuntimeState()
        state = state.reduce(activityEvent(seq = 0, id = 1, stage = Stage.CREATED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.RESUMED))

        val event = fragmentEvent(seq = 2, id = 2, stage = Stage.CREATED, hostId = 1)
        val risk = StateLossRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
