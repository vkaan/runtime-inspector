package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object IccTakenOutEarlyRule : RiskRule {

    override val id = "CARD_REMOVED_DURING_TRANSACTION"

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
