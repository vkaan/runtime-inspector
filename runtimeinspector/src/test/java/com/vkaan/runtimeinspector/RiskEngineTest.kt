package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskEngine
import com.vkaan.runtimeinspector.rules.RiskRule
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskEngineTest {

    // Fires on every Network LOST event; the first transport entry doubles as the subject so
    // tests can steer which dedup key the risk lands on.
    private val rule = object : RiskRule {
        override val id = "TEST_RULE"
        override fun evaluate(event: RuntimeEvent, before: RuntimeState, after: RuntimeState): Risk? {
            if (event !is RuntimeEvent.Network) return null
            if (event.state != RuntimeEvent.Network.State.LOST) return null
            return Risk(
                ruleId = id,
                severity = Risk.Severity.WARNING,
                message = "test finding",
                subject = event.transports.firstOrNull(),
                seq = event.seq,
                timestampMillis = event.timestampMillis,
            )
        }
    }

    private val state = RuntimeState()

    private fun fire(engine: RiskEngine, seq: Long, subject: String = "screenA") {
        val event = networkEvent(seq, RuntimeEvent.Network.State.LOST, transports = listOf(subject))
        engine.onEvent(event, state, state)
    }

    @Test
    fun `repeat occurrences update the finding instead of being dropped`() {
        val engine = RiskEngine(rules = listOf(rule))
        fire(engine, seq = 1)
        fire(engine, seq = 5)
        fire(engine, seq = 9)

        val risk = engine.snapshot().single()
        assertEquals(3, risk.occurrences)
        assertEquals(1L, risk.seq)
        assertEquals(1L, risk.timestampMillis)
        assertEquals(9L, risk.lastSeq)
        assertEquals(9L, risk.lastTimestampMillis)
    }

    @Test
    fun `different subjects stay separate findings`() {
        val engine = RiskEngine(rules = listOf(rule))
        fire(engine, seq = 1, subject = "screenA")
        fire(engine, seq = 2, subject = "screenB")

        val findings = engine.snapshot()
        assertEquals(2, findings.size)
        assertEquals(listOf(1, 1), findings.map { it.occurrences })
    }

    @Test
    fun `capacity evicts the oldest finding while repeats do not grow the list`() {
        val engine = RiskEngine(rules = listOf(rule), capacity = 2)
        fire(engine, seq = 1, subject = "a")
        fire(engine, seq = 2, subject = "b")
        fire(engine, seq = 3, subject = "a") // repeat of "a": count goes up, size stays 2
        fire(engine, seq = 4, subject = "c") // over capacity: evicts "a" (first inserted)

        val findings = engine.snapshot()
        assertEquals(listOf("b", "c"), findings.map { it.subject })
    }

    @Test
    fun `listener is called for a new finding`() {
        val reported = mutableListOf<Risk>()
        val engine = RiskEngine(rules = listOf(rule), onReport = { reported += it })

        fire(engine, seq = 1)

        assertEquals(1, reported.size)
        assertEquals("TEST_RULE", reported.single().ruleId)
        assertEquals("screenA", reported.single().subject)
    }

    @Test
    fun `repeats reach the listener only at milestones`() {
        val reported = mutableListOf<Risk>()
        val engine = RiskEngine(rules = listOf(rule), onReport = { reported += it })

        // Occurrences 1..5: only the very first is reportable.
        repeat(5) { i -> fire(engine, seq = i.toLong()) }
        assertEquals(1, reported.size)

        // Occurrences 6..10: the 10th is a milestone, so exactly one more arrives.
        repeat(5) { i -> fire(engine, seq = 5L + i) }
        assertEquals(2, reported.size)
        assertEquals(10, reported.last().occurrences)
    }

    @Test
    fun `a throwing listener does not break the engine`() {
        val seqsSeenBySecondRule = mutableListOf<Long>()
        val secondRule = object : RiskRule {
            override val id = "SECOND_RULE"
            override fun evaluate(
                event: RuntimeEvent,
                before: RuntimeState,
                after: RuntimeState,
            ): Risk? {
                seqsSeenBySecondRule += event.seq
                return null
            }
        }
        val engine = RiskEngine(
            rules = listOf(rule, secondRule),
            onReport = { error("listener boom") },
        )

        fire(engine, seq = 1)

        // The finding was still recorded, and the rule after the throwing report still ran.
        assertEquals(1, engine.snapshot().size)
        assertEquals(listOf(1L), seqsSeenBySecondRule)
    }
}
