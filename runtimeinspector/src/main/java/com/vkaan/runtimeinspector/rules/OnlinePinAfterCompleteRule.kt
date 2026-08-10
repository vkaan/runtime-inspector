package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object OnlinePinAfterCompleteRule : RiskRule {

    override val id = "ONLINE_PIN_AFTER_COMPLETE"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.GET_ONLINE_PIN !in event.apis) return null
        if (event.to == CardServiceState.READ_CARD) return null
        if (before.cardServiceState != CardServiceState.COMPLETED) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "An online PIN was requested after completeEmvTxn — the transaction is " +
                "already finalised and the PIN cannot belong to it.",
            subject = CardServiceApi.GET_ONLINE_PIN.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
