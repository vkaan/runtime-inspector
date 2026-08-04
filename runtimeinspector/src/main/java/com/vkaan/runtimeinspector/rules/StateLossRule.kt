package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object StateLossRule : RiskRule {

    override val id = "STATE_LOSS"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Lifecycle) return null
        if (event.sourceType != SourceType.FRAGMENT || event.stage != Stage.CREATED) return null
        val hostId = event.hostActivityId ?: return null
        if (before.activityStages[hostId] != Stage.STOPPED) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "Fragment created while its host Activity is STOPPED — " +
                "likely a commit after onSaveInstanceState; state can be silently lost.",
            subject = event.name,
            instanceId = event.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
