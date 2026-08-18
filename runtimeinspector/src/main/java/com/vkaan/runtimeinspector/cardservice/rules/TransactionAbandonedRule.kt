package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object TransactionAbandonedRule : RiskRule {

    override val id = "PREVIOUS_TRANSACTION_NOT_FINISHED"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (!before.cardTransactionOpen) return null
        if (event.to != CardServiceState.READ_CARD) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "A new card read started while ${before.cardServiceState.name} was still " +
                    "open — the previous transaction never reached completeEmvTxn.",
            subject = before.cardServiceState.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )

    }
}