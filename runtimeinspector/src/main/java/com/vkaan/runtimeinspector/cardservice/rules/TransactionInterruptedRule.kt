package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object TransactionInterruptedRule : RiskRule {

    override val id = "TRANSACTION_INTERRUPTED"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (!after.cardTransactionOpen) return null

        val cause = when {
            event is RuntimeEvent.Crash -> "crash"

            event is RuntimeEvent.SystemSignal &&
                event.signal == RuntimeEvent.SystemSignal.Signal.SCREEN_OFF -> "screen-off"

            event is RuntimeEvent.Lifecycle &&
                event.sourceType == SourceType.ACTIVITY &&
                event.stage == Stage.DESTROYED &&
                event.isChangingConfigurations == true -> "recreation"

            else -> return null
        }

        val state = after.cardServiceState

        return Risk(
            ruleId = id,
            severity = if (cause == "crash") Risk.Severity.ERROR else Risk.Severity.WARNING,
            message = "Card transaction was in ${state.name} when the app hit $cause — the " +
                    "terminal may be committed at the acquirer with no UI left to finish it.",
            subject = "${state.name}:$cause",
            instanceId = after.foregroundScreen?.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
