package com.vkaan.runtimeinspector.rules

import android.util.Log
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal class RiskEngine (
    private val rules: List<RiskRule> = DEFAULT_RULES,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private companion object {
        const val TAG = "RuntimeInspector"
        const val DEFAULT_CAPACITY = 100

        val DEFAULT_RULES: List<RiskRule> = listOf(
            StateLossRule,
            BackStackGrowthRule,
            MemoryPressureRule,
            RecreationMidFlowRule,
            DuplicateScreenRule,
        )
    }

    private val lock = Any()
    private val fired = mutableSetOf<String>()
    private val findings = ArrayDeque<Risk>(capacity)

    fun onEvent(event: RuntimeEvent, before: RuntimeState, after: RuntimeState)
    {
        for (rule in rules) {
            val risk = try {
                rule.evaluate(event, before, after)
            } catch (t: Throwable) {
                Log.e(TAG, "Rule ${rule.id} threw - skipping.", t)
                null
            } ?: continue

            val isNew = synchronized(lock) {
                if (!fired.add(risk.dedupKey)) {
                    false
                } else {
                    findings.addLast(risk)
                    while (findings.size > capacity) findings.removeFirst()
                    true
                }
            }

            if (isNew) {
                when (risk.severity) {
                    Risk.Severity.ERROR -> Log.e(TAG, risk.logLine())
                    else -> Log.w(TAG, risk.logLine())
                }
            }
        }
    }

    fun snapshot(): List<Risk> = synchronized(lock) { findings.toList() }
}