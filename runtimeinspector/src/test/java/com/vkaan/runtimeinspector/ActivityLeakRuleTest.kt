package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.ActivityLeakRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.MemoryUsage.Trigger
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityLeakRuleTest {

    @Test
    fun `fires when heap climbs at least 1MB per destroy while live count is flat`() {
        var state = RuntimeState()
        state = state.reduce(heapEvent(seq = 0, usedBytes = mb(10), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))
        state = state.reduce(heapEvent(seq = 1, usedBytes = mb(12), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))

        val event = heapEvent(seq = 2, usedBytes = mb(14), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED)
        val risk = ActivityLeakRule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when the climb is below the margin`() {
        var state = RuntimeState()
        state = state.reduce(heapEvent(seq = 0, usedBytes = mb(10), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))
        state = state.reduce(heapEvent(seq = 1, usedBytes = mb(12), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))

        val event = heapEvent(seq = 2, usedBytes = mb(12) + 100, maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED)
        val risk = ActivityLeakRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent when the live activity count is not flat`() {
        var state = RuntimeState()
        state = state.reduce(heapEvent(seq = 0, usedBytes = mb(10), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))
        state = state.reduce(activityEvent(seq = 1, id = 1, stage = Stage.CREATED))
        state = state.reduce(heapEvent(seq = 2, usedBytes = mb(12), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))

        val event = heapEvent(seq = 3, usedBytes = mb(14), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED)
        val risk = ActivityLeakRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `stays silent with fewer than three samples`() {
        var state = RuntimeState()
        state = state.reduce(heapEvent(seq = 0, usedBytes = mb(10), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED))

        val event = heapEvent(seq = 1, usedBytes = mb(12), maxBytes = mb(100), trigger = Trigger.ACTIVITY_DESTROYED)
        val risk = ActivityLeakRule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
