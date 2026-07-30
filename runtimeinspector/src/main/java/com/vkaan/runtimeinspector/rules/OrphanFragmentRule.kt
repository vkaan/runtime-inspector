package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object OrphanFragmentRule : RiskRule {

    override val id = "ORPHAN_FRAGMENT"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Lifecycle) return null
        if (event.sourceType != SourceType.ACTIVITY || event.stage != Stage.DESTROYED) return null
        val orphanIds = after.fragmentHostIds.filterValues { it == event.instanceId }.keys
        if (orphanIds.isEmpty()) return null

        val names = orphanIds.mapNotNull { after.liveFragmentIds[it] }.distinct()

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "${orphanIds.size} fragment(s) still alive after their host Activity " +
                "was destroyed: ${names.joinToString().ifEmpty { "unknown" }} — likely leaked.",
            subject = "${event.name}#${event.instanceId}",
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
