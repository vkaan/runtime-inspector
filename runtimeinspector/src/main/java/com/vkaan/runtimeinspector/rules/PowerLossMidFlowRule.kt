package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.SystemSignal.Signal
import com.vkaan.runtimeinspector.timeline.RuntimeState

/**
 * Android cannot stop a transaction from starting on a dying battery. BATTERY_LOW mid-flow
 * warns the terminal may die mid-transaction; SHUTDOWN mid-flow means it is happening now.
 */
internal object PowerLossMidFlowRule : RiskRule {

    override val id = "POWER_LOSS_MID_FLOW"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.SystemSignal) return null
        if (event.signal != Signal.BATTERY_LOW && event.signal != Signal.SHUTDOWN) return null
        if (after.backStackDepths.values.none { it > 0 }) return null

        val screen = after.foregroundScreen
        val shuttingDown = event.signal == Signal.SHUTDOWN

        return Risk(
            ruleId = id,
            severity = if (shuttingDown) Risk.Severity.ERROR else Risk.Severity.WARNING,
            message = if (shuttingDown) {
                "Device is shutting down while a flow is open — the transaction is being " +
                    "interrupted right now."
            } else {
                "Battery is low while a flow is open — the terminal may die mid-transaction."
            },
            subject = screen?.name ?: "app",
            instanceId = screen?.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
