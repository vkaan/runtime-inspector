package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object NetworkLossRule : RiskRule {

    override val id = "NETWORK_LOSS"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Network) return null
        if (event.state != RuntimeEvent.Network.State.LOST) return null
        if (!after.appInForeground) return null

        val midFlow = after.backStackDepths.values.any { it > 0 }
        val screen = after.foregroundScreen ?: "app"

        return Risk(
            ruleId = id,
            severity = if (midFlow) Risk.Severity.WARNING else Risk.Severity.INFO,
            message = if (midFlow) {
                "Network lost while a flow was open — any in-flight request is now in limbo; " +
                    "its result may never reach the UI."
            } else {
                "Network lost while the app was foregrounded."
            },
            subject = if (midFlow) "$screen:mid-flow" else screen,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
