package com.vkaan.runtimeinspector.rules

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal interface RiskRule {
    val id: String
    fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk?
}