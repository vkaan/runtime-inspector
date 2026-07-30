package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object ActivityLeakRule : RiskRule {

    private const val MIN_STEP_BYTES = 1L * 1024 * 1024

    override val id = "ACTIVITY_LEAK"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.MemoryUsage) return null
        if (event.trigger != RuntimeEvent.MemoryUsage.Trigger.ACTIVITY_DESTROYED) return null
        val samples = after.destroyHeapSamples
        if (samples.size < 3) return null

        val counts = samples.map { it.liveActivities }
        if (counts.distinct().size != 1) return null

        val climbing = samples.zipWithNext().all { (a, b) -> b.usedBytes - a.usedBytes >= MIN_STEP_BYTES }
        if (!climbing) return null

        val risenMb = (samples.last().usedBytes - samples.first().usedBytes) / 1024 / 1024

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "Heap climbed ${risenMb}MB across ${samples.size} Activity destructions " +
                "while the live Activity count stayed at ${counts.first()} — " +
                "destroyed Activities may still be referenced (leak).",
            subject = "heap-trend",
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
