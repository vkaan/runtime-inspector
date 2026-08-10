package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object CardServiceCallBeforeBindRule : RiskRule {

    override val id = "CARD_SERVICE_CALL_BEFORE_BIND"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (before.cardServiceBound) return null

        val called = event.apis.firstOrNull { it != CardServiceApi.BIND } ?: return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "${called.name} was called before the card service reported a bound " +
                "client — the call cannot have reached the service.",
            subject = called.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
