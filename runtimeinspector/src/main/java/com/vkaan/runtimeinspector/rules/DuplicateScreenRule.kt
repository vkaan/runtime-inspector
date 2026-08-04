package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object DuplicateScreenRule : RiskRule {

    override val id = "DUPLICATE_SCREEN"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Lifecycle) return null
        if (event.sourceType != SourceType.ACTIVITY || event.stage != Stage.CREATED) return null
        val copies = after.liveActivityIds.values.count { it == event.name }
        if (copies < 2) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "$copies live instances of ${event.name} at once — " +
                "double-tap launch or launchMode misconfiguration.",
            subject = event.name,
            instanceId = event.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
