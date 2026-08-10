package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object IccTakenOutEarlyRule : RiskRule {

    override val id = "ICC_TAKEN_OUT_EARLY"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.TAKE_OUT_ICC !in event.apis) return null
        if (!before.cardTransactionOpen) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "The card was released while ${before.cardServiceState.name} was still " +
                "open — the chip is gone before completeEmvTxn can apply the issuer response.",
            subject = before.cardServiceState.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
