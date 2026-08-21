package com.vkaan.runtimeinspector.rules

import android.util.Log
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceBoundTwiceRule
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceCallAfterStopRule
import com.vkaan.runtimeinspector.cardservice.rules.CardServiceCallBeforeBindRule
import com.vkaan.runtimeinspector.cardservice.rules.EmvClConfigOrderRule
import com.vkaan.runtimeinspector.cardservice.rules.IccTakenOutEarlyRule
import com.vkaan.runtimeinspector.cardservice.rules.OnlinePinAfterCompleteRule
import com.vkaan.runtimeinspector.cardservice.rules.TransactionAbandonedRule
import com.vkaan.runtimeinspector.cardservice.rules.TransactionInterruptedRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState

internal class RiskEngine(
    private val rules: List<RiskRule>,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val onReport: ((Risk) -> Unit)? = null,
) {

    companion object {
        private const val TAG = "RuntimeInspector"
        private const val DEFAULT_CAPACITY = 100

        fun withDefaultRules(
            backStackCeiling: Int,
            heapPercentCeiling: Int,
            networkFlapCount: Int,
            networkFlapWindowSeconds: Int,
            onReport: ((Risk) -> Unit)? = null,

        ): RiskEngine =
            RiskEngine(
                rules = listOf(
                    StateLossRule,
                    BackStackGrowthRule(backStackCeiling),
                    MemoryPressureRule(heapPercentCeiling),
                    NetworkFlappingRule(networkFlapCount, networkFlapWindowSeconds),
                    RecreationMidFlowRule,
                    DuplicateScreenRule,
                    InterruptedFlowRule,
                    OrphanFragmentRule,
                    ActivityLeakRule,
                    NetworkLossRule,
                    MidFlowCrashRule,
                    ScreenOffMidFlowRule,
                    PowerLossMidFlowRule,
                    TransactionInterruptedRule,
                    CardServiceCallBeforeBindRule,
                    CardServiceBoundTwiceRule,
                    CardServiceCallAfterStopRule,
                    EmvClConfigOrderRule,
                    OnlinePinAfterCompleteRule,
                    IccTakenOutEarlyRule,
                    TransactionAbandonedRule,
                ),
                onReport = onReport,
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
                    updated
                }
            }

            val line = toLog.logLine() +
                if (toLog.occurrences > 1) " (×${toLog.occurrences})" else ""
            when (toLog.severity) {
                Risk.Severity.ERROR -> Log.e(TAG, line)
                else -> Log.w(TAG, line)
            }

            try {
                onReport?.invoke(toLog)
            } catch (t: Throwable) {
                Log.e(TAG, "Risk listener threw - continuing.", t)
            }
        }
    }

    fun snapshot(): List<Risk> = synchronized(lock) { findings.values.toList() }

    fun clear() = synchronized(lock) { findings.clear() }
}