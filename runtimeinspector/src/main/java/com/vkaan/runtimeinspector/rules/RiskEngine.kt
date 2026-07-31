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
        private val LOG_MILESTONES = setOf(10, 100, 1_000, 10_000)

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
    /** One finding per dedupKey, in first-seen order; repeats update the entry in place. */
    private val findings = LinkedHashMap<String, Risk>()

    fun onEvent(event: RuntimeEvent, before: RuntimeState, after: RuntimeState)
    {
        for (rule in rules) {
            val risk = try {
                rule.evaluate(event, before, after)
            } catch (t: Throwable) {
                Log.e(TAG, "Rule ${rule.id} threw - skipping.", t)
                null
            } ?: continue

            val toLog = synchronized(lock) {
                val existing = findings[risk.dedupKey]
                if (existing == null) {
                    findings[risk.dedupKey] = risk
                    while (findings.size > capacity) findings.remove(findings.keys.first())
                    risk
                } else {
                    val updated = existing.copy(
                        occurrences = existing.occurrences + 1,
                        lastSeq = risk.seq,
                        lastTimestampMillis = risk.timestampMillis,
                    )
                    findings[risk.dedupKey] = updated
                    // Re-logging every repeat would flood logcat; milestones keep the log
                    // readable while still showing that a finding keeps happening.
                    updated.takeIf { it.occurrences in LOG_MILESTONES }
                }
            } ?: continue

            val line = toLog.logLine() +
                if (toLog.occurrences > 1) " (×${toLog.occurrences})" else ""
            when (toLog.severity) {
                Risk.Severity.ERROR -> Log.e(TAG, line)
                else -> Log.w(TAG, line)
            }
        }
    }

    fun snapshot(): List<Risk> = synchronized(lock) { findings.values.toList() }
}