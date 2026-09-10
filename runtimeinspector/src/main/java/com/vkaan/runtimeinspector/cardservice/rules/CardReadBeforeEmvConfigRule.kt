package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object CardReadBeforeEmvConfigRule : RiskRule {

    override val id = "CARD_READ_BEFORE_EMV_CONFIG"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.GET_CARD !in event.apis) return null
        // getCard without a bind is CardServiceCallBeforeBindRule's finding, not this one.
        if (!before.cardServiceBound) return null
        if (before.emvConfigured && before.emvClConfigured) return null

        val missing = buildList {
            if (!before.emvConfigured) add("setEMVConfiguration")
            if (!before.emvClConfigured) add("setEMVCLConfiguration")
        }.joinToString(" + ")

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "getCard was called before EMV configuration was fully initialized — " +
                    "$missing missing; on-us cards fail to read.",
            subject = CardServiceApi.GET_CARD.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
