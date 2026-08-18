package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

/**
 * NETWORK_LOSS reports what a drop does to the app; this reports what repeated drops say
 * about the link. Cycling means a bad SIM, antenna or coverage, so it fires regardless of
 * foreground state and its subject is the link itself.
 */
internal class NetworkFlappingRule(
    private val lossCount: Int,
    private val windowSeconds: Int,
) : RiskRule {

    override val id = "NETWORK_DROPPING_REPEATEDLY"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Network) return null
        if (event.state != RuntimeEvent.Network.State.LOST) return null

        val cutoff = event.elapsedRealtimeNanos - windowSeconds * 1_000_000_000L
        val lossesInWindow = after.recentNetworkLossNanos.count { it >= cutoff }
        if (lossesInWindow < lossCount) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "Network lost $lossesInWindow times within ${windowSeconds}s — the " +
                "connection is unstable; in-flight requests keep dying mid-transaction.",
            subject = "network",
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
