package com.vkaan.runtimeinspector.collector

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.Timeline

internal class ProcessLifecycleCollector (
    private val timeline: Timeline
) : Collector {

    private val observer = LifecycleEventObserver { _ , event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> record(RuntimeEvent.Process.State.CREATED)
            Lifecycle.Event.ON_START  -> record(RuntimeEvent.Process.State.FOREGROUNDED)
            Lifecycle.Event.ON_STOP   -> record(RuntimeEvent.Process.State.BACKGROUNDED)
            else -> Unit
        }
    }


    override fun start(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    override fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
    }

    private fun record(state: RuntimeEvent.Process.State) {
        timeline.record { seq ->
            RuntimeEvent.Process(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                state = state,
            )
        }
    }
}