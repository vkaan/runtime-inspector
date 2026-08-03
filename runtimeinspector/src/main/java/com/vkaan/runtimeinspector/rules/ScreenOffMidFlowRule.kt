package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

/**
 * The screen dying mid-flow usually means an idle timeout during customer interaction (PIN
 * entry, amount confirmation) — the flow will time out and any in-flight auth gets reversed.
 */
internal object ScreenOffMidFlowRule : RiskRule {

    override val id = "SCREEN_OFF_MID_FLOW"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.SystemSignal) return null
        if (event.signal != RuntimeEvent.SystemSignal.Signal.SCREEN_OFF) return null
        if (!after.appInForeground) return null
        if (after.backStackDepths.values.none { it > 0 }) return null

        val screen = after.foregroundScreen ?: "app"

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "Screen turned off while a flow was open — an idle timeout mid-interaction; " +
                "the flow will expire and any in-flight request may be reversed.",
            subject = screen,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
