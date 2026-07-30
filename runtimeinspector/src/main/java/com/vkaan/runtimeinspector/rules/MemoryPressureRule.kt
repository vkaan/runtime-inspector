package com.vkaan.runtimeinspector.rules

import android.content.ComponentCallbacks2
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal class MemoryPressureRule(
    private val heapPercentCeiling: Int,
) : RiskRule {

    override val id = "MEMORY_PRESSURE"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event is RuntimeEvent.Memory) {
            if (event.level != ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) return null
            if (!after.appInForeground) return null
            return Risk(
                ruleId = id,
                severity = Risk.Severity.ERROR,
                message = "onTrimMemory(RUNNING_CRITICAL) while foregrounded — " +
                    "the OS is close to killing processes; frozen UI is the typical symptom.",
                subject = "trim",
                seq = event.seq,
                timestampMillis = event.timestampMillis,
            )
        }
        if (event is RuntimeEvent.MemoryUsage) {
            if (event.usedPercent < heapPercentCeiling) return null
            return Risk(
                ruleId = id,
                severity = Risk.Severity.WARNING,
                message = "Java heap at ${event.usedPercent}% of max — " +
                    "GC thrash and OutOfMemoryError territory.",
                subject = "heap",
                seq = event.seq,
                timestampMillis = event.timestampMillis,
            )
        }
        return null
    }
}
