package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object RecreationMidFlowRule : RiskRule {

    override val id = "RECREATION_MID_FLOW"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Lifecycle) return null
        if (event.sourceType != SourceType.ACTIVITY || event.stage != Stage.DESTROYED) return null
        if (event.isChangingConfigurations != true) return null
        val depth = before.backStackDepths[event.instanceId] ?: 0
        if (depth == 0) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "Activity destroyed for a config change while its back stack held " +
                "$depth entries — in-flight callbacks and results can be lost; " +
                "verify state restoration.",
            subject = event.name,
            instanceId = event.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
