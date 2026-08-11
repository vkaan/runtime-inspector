package com.vkaan.runtimeinspector.collector

import android.os.SystemClock
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.EventSink

internal object HeapSampler {

    fun sample (timeline: EventSink, trigger: RuntimeEvent.MemoryUsage.Trigger) {
        val runtime = Runtime.getRuntime()

        val max = runtime.maxMemory()
        val used = runtime.totalMemory() -  runtime.freeMemory()

        timeline.record { seq ->
            RuntimeEvent.MemoryUsage(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                usedBytes = used,
                maxBytes = max,
                trigger = trigger,
            )
        }


    }
}