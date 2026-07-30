package com.vkaan.runtimeinspector

import android.content.ComponentCallbacks2
import com.vkaan.runtimeinspector.rules.MemoryPressureRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.MemoryUsage.Trigger
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryPressureRuleTest {

    private val rule = MemoryPressureRule(heapPercentCeiling = 85)

    @Test
    fun `fires as ERROR on critical trim while foregrounded`() {
        var state = RuntimeState()
        state = state.reduce(processEvent(seq = 0, state = RuntimeEvent.Process.State.FOREGROUNDED))

        val event = memoryEvent(seq = 1, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
    }

    @Test
    fun `stays silent on critical trim while backgrounded`() {
        val state = RuntimeState()

        val event = memoryEvent(seq = 0, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }

    @Test
    fun `fires as WARNING when heap crosses the ceiling`() {
        val state = RuntimeState()

        val event = heapEvent(seq = 0, usedBytes = mb(90), maxBytes = mb(100), trigger = Trigger.APP_FOREGROUNDED)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertEquals(Risk.Severity.WARNING, risk?.severity)
    }

    @Test
    fun `stays silent when heap is below the ceiling`() {
        val state = RuntimeState()

        val event = heapEvent(seq = 0, usedBytes = mb(50), maxBytes = mb(100), trigger = Trigger.APP_FOREGROUNDED)
        val risk = rule.evaluate(event, before = state, after = state.reduce(event))

        assertNull(risk)
    }
}
