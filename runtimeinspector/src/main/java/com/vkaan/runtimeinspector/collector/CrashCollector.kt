package com.vkaan.runtimeinspector.collector

import android.app.Application
import android.os.SystemClock
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.EventSink

internal class CrashCollector(
    private val timeline: EventSink,
) : Collector, Thread.UncaughtExceptionHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    override fun start(application: Application) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun stop() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        previousHandler = null
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            timeline.record { seq ->
                RuntimeEvent.Crash(
                    seq = seq,
                    timestampMillis = System.currentTimeMillis(),
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                    threadName = thread.name,
                    exceptionClass = throwable.javaClass.name,
                    exceptionMessage = throwable.message,
                    topFrame = throwable.stackTrace.firstOrNull()?.toString(),
                )
            }
        } catch (_: Throwable) {
            // A second failure here must never eat the original crash.
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
