package com.vkaan.runtimeinspector.timeline

import android.util.Log
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskEngine

internal class Timeline(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val riskEngine: RiskEngine = RiskEngine(),
) {

    private companion object {
        const val TAG = "RuntimeInspector"
        const val DEFAULT_CAPACITY = 500

    }

    private val lock = Any()
    private val buffer = ArrayDeque<RuntimeEvent>(capacity)
    private var nextSeq = 0L
    private var state = RuntimeState()

    fun record(build: (seq: Long) -> RuntimeEvent) {
        val event: RuntimeEvent
        val before: RuntimeState
        val after: RuntimeState
        synchronized(lock) {
            event = build(nextSeq++)
            buffer.addLast(event)
            while (buffer.size > capacity) buffer.removeFirst()
            before = state
            state = state.reduce(event)
            after = state
        }
        Log.d(TAG, event.logLine())
        riskEngine.onEvent(event, before, after)
    }

    fun snapshot(): List<RuntimeEvent> = synchronized(lock) {
        buffer.toList()
    }

    fun state(): RuntimeState = synchronized(lock) { state }

    fun risks(): List<Risk> = riskEngine.snapshot()
}