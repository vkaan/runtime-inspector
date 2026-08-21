package com.vkaan.runtimeinspector.cardservice.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

/**
 * A bind arrived while a client was still bound — no unbound line came in between. A normal
 * reconnect is bound → unbound → bound; skipping the unbound means the previous connection was
 * never released, so it leaks and every callback can fire twice.
 */
internal object CardServiceBoundTwiceRule : RiskRule {

    override val id = "CARD_SERVICE_BOUND_TWICE"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.BIND !in event.apis) return null
        if (!before.cardServiceBound) return null

        return Risk(
            ruleId = id,
            severity = Risk.Severity.WARNING,
            message = "The card service was bound again while a client was already bound — no " +
                    "unbind came in between. The previous connection is never released, so it " +
                    "leaks and its callbacks can fire twice.",
            subject = null,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
