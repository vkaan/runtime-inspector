package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceCallAfterStopRule
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardServiceCallAfterStopRuleTest {

    private val rule = CardServiceCallAfterStopRule

    private fun hostAt(vararg stages: Stage): RuntimeState {
        var state = RuntimeState()
        stages.forEachIndexed { index, stage ->
            state = state.reduce(
                activityEvent(seq = index.toLong(), id = 1, name = "PaymentActivity", stage = stage)
            )
        }
        return state
    }

    private val call = cardServiceCall(seq = 9, apis = listOf(CardServiceApi.GET_CARD))

    @Test
    fun `fires when a call arrives while the host Activity is stopped`() {
        val state = hostAt(Stage.CREATED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)

        val risk = rule.evaluate(call, before = state, after = state.reduce(call))

        assertEquals(Risk.Severity.ERROR, risk?.severity)
        assertEquals("GET_CARD:STOPPED", risk?.subject)
    }

    @Test
    fun `stays silent while the host Activity is resumed`() {
        val state = hostAt(Stage.CREATED, Stage.RESUMED)

        val risk = rule.evaluate(call, before = state, after = state.reduce(call))

        assertNull(risk)
    }

    @Test
    fun `reports a destroyed host when no Activity is left`() {
        val state = hostAt(Stage.CREATED, Stage.RESUMED, Stage.DESTROYED)

        val risk = rule.evaluate(call, before = state, after = state.reduce(call))

        assertEquals("GET_CARD:DESTROYED", risk?.subject)
    }

    @Test
    fun `stays silent before the app has shown any Activity`() {
        val state = RuntimeState()

        val risk = rule.evaluate(call, before = state, after = state.reduce(call))

        assertNull(risk)
    }
}
