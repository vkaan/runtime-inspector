package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object InterruptedFlowRule : RiskRule {

    override val id = "INTERRUPTED_FLOW"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Process) return null
        if (event.state != RuntimeEvent.Process.State.BACKGROUNDED) return null
        val deepest = before.backStackDepths.values.maxOrNull() ?: 0
        if (deepest == 0) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.INFO,
            message = "App backgrounded while a navigation flow was open " +
                "(back stack depth $deepest) — if the process is killed now, " +
                "pending results and callbacks are lost.",
            subject = before.foregroundActivity?.name ?: "app",
            instanceId = before.foregroundActivity?.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
