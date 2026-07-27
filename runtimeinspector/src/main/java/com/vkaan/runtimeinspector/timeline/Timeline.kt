package com.vkaan.runtimeinspector.timeline

import android.util.Log

internal class Timeline(private val capacity: Int = DEFAULT_CAPACITY) {

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
        synchronized(lock) {
            event = build(nextSeq++)
            buffer.addLast(event)
            while (buffer.size > capacity) buffer.removeFirst()
            state = state.reduce(event)

        }
        Log.d(TAG, event.logLine())
    }

    fun snapshot(): List<RuntimeEvent> = synchronized(lock) {
        buffer.toList()
    }

    fun state(): RuntimeState = synchronized(lock) { state }
}