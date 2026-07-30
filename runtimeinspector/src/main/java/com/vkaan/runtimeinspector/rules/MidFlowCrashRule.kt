package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal object MidFlowCrashRule : RiskRule {

    override val id = "MID_FLOW_CRASH"

    override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
        if (event !is RuntimeEvent.Crash) return null
        val midFlow = before.backStackDepths.values.any { it > 0 }
        val simpleName = event.exceptionClass.substringAfterLast('.')

        return Risk(
            ruleId = id,
            severity = if (midFlow) Risk.Severity.ERROR else Risk.Severity.WARNING,
            message = if (midFlow) {
                "Uncaught $simpleName while a flow was open — the app died mid-transaction."
            } else {
                "Uncaught $simpleName on thread ${event.threadName}."
            },
            subject = before.foregroundScreen ?: "app",
            seq = event.seq,
            timestampMillis = event.timestampMillis,
        )
    }
}
