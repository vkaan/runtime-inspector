package com.vkaan.runtimeinspector.rules

import android.util.Log
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal class RiskEngine(
    private val rules: List<RiskRule>,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    companion object {
        private const val TAG = "RuntimeInspector"
        private const val DEFAULT_CAPACITY = 100

        fun withDefaultRules(backStackCeiling: Int, heapPercentCeiling: Int): RiskEngine =
            RiskEngine(
                rules = listOf(
                    StateLossRule,
                    BackStackGrowthRule(backStackCeiling),
                    MemoryPressureRule(heapPercentCeiling),
                    RecreationMidFlowRule,
                    DuplicateScreenRule,
                    InterruptedFlowRule,
                    OrphanFragmentRule,
                    ActivityLeakRule,
                    NetworkLossRule,
                    MidFlowCrashRule,
                ),
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