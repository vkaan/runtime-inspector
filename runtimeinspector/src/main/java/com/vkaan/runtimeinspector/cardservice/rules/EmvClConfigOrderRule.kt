package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object EmvClConfigOrderRule : RiskRule {

    override val id = "CONTACTLESS_CONFIG_BEFORE_CONTACT_CONFIG"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.SET_EMV_CL_CONFIG !in event.apis) return null
        if (before.emvConfigured) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "Contactless EMV was configured before contact EMV — " +
                    "setEMVConfiguration must come first or the kernel is left unconfigured.",
            subject = CardServiceApi.SET_EMV_CL_CONFIG.name,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
