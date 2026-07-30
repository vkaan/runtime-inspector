package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal class BackStackGrowthRule(
    private val ceiling: Int,
) : RiskRule {

    override val id = "BACKSTACK_GROWTH"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Lifecycle) return null
        val count = event.backStackEntryCount ?: return null
        if (count < ceiling) return null

        val hostId = when (event.sourceType) {
            SourceType.ACTIVITY -> event.instanceId
            SourceType.FRAGMENT -> event.hostActivityId ?: return null
        }
        val hostName = after.liveActivityIds[hostId] ?: "Activity"

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "Back stack depth reached $count (ceiling $ceiling) — " +
                "screens are stacking without being popped.",
            subject = "$hostName#$hostId",
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
