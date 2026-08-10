package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object CardServiceCallAfterStopRule : RiskRule {

    override val id = "CARD_SERVICE_CALL_AFTER_STOP"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.CardService) return null
        if (CardServiceApi.BIND in event.apis) return null

        val host = after.foregroundActivity
        val hostStage = host?.let { after.activityStages[it.instanceId] }

        val stageName = when {
            host == null && after.peakLiveActivities == 0 -> return null
            host == null -> Stage.DESTROYED.name
            hostStage == null || hostStage == Stage.RESUMED -> return null
            else -> hostStage.name
        }

        val called = event.apis.joinToString("|") { it.name }

        return Risk(
            ruleId = id,
            severity = Risk.Severity.ERROR,
            message = "$called reached the card service while the calling Activity was " +
                "$stageName — the service rejects calls made after the host is paused.",
            subject = "$called:$stageName",
            instanceId = host?.instanceId,
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
