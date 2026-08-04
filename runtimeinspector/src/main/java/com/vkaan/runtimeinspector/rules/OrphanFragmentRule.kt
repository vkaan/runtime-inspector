package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object OrphanFragmentRule : RiskRule {

    private const val GRACE_NANOS = 1_000_000_000L

    override val id = "ORPHAN_FRAGMENT"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        val expired = after.orphanCandidates.filterValues { hostDiedAt ->
            event.elapsedRealtimeNanos - hostDiedAt >= GRACE_NANOS
        }
        if (expired.isEmpty()) return null

        val oldest = expired.entries.minWith(compareBy({ it.value }, { it.key }))
        val name = after.liveFragmentIds[oldest.key] ?: "Fragment"

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "$name still alive over 1s after its host Activity was destroyed — " +
                "something is holding a reference to it (likely leaked).",
            subject = name,
            instanceId = oldest.key,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
