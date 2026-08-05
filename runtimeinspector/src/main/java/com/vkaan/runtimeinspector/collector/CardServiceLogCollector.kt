package com.vkaan.runtimeinspector.collector

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.vkaan.runtimeinspector.cardservice.CardServiceLogPattern
import com.vkaan.runtimeinspector.cardservice.CardServiceLogState
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.Timeline
import java.io.IOException

internal class CardServiceLogCollector(
    private val timeline: Timeline,
    private val tags: List<String>,
    patterns: List<CardServiceLogPattern>,
) : Collector {

    private companion object {
        const val TAG = "RuntimeInspector"
    }

    private val tracker = CardServiceLogState(patterns)

    @Volatile
    private var running = false
    private var process: Process? = null

    override fun start(application: Application) {
        running = true
        Thread(::read, "RuntimeInspector-cardservice").apply {
            isDaemon = true
            start()
        }
    }

    private fun read() {
        val command = listOf("logcat", "-v", "raw", "-T", "1") + tags.map { "$it:V" } + "*:S"
        try {
            val started = ProcessBuilder(command).redirectErrorStream(true).start()
            process = started
            Log.i(TAG, "Card service log reader started on ${tags.joinToString("|")}")
            started.inputStream.bufferedReader().forEachLine(::onLine)
        } catch (e: IOException) {
            if (running) Log.w(TAG, "Card service log reader stopped: ${e.message}")
        }
    }

    private fun onLine(line: String) {
        if (!running) return
        val transition = tracker.onLine(line, SystemClock.elapsedRealtimeNanos()) ?: return
        timeline.record { seq ->
            RuntimeEvent.CardService(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = transition.elapsedRealtimeNanos,
                from = transition.from,
                to = transition.to,
                line = transition.line,
            )
        }
    }

    override fun stop() {
        running = false
        process?.destroy()
        process = null
    }
}
