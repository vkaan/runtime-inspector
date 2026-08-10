package com.vkaan.runtimeinspector.collector

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.SystemSignal.Signal
import com.vkaan.runtimeinspector.timeline.Timeline

internal class SystemBroadcastCollector(
    private val timeline: Timeline,
) : Collector {

    private var application: Application? = null

    // All five actions are system broadcasts: only the OS can send them, no permission is
    // needed, and they are exempt from the exported-flag requirement on newer target SDKs.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val signal = when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> Signal.SCREEN_ON
                Intent.ACTION_SCREEN_OFF -> Signal.SCREEN_OFF
                Intent.ACTION_BATTERY_LOW -> Signal.BATTERY_LOW
                Intent.ACTION_BATTERY_OKAY -> Signal.BATTERY_OKAY
                Intent.ACTION_SHUTDOWN -> Signal.SHUTDOWN
                else -> return
            }
            timeline.record { seq ->
                RuntimeEvent.SystemSignal(
                    seq = seq,
                    timestampMillis = System.currentTimeMillis(),
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                    signal = signal,
                )
            }
        }
    }

    override fun start(application: Application) {
        this.application = application
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        application.registerReceiver(receiver, filter)
    }

    override fun stop() {
        application?.unregisterReceiver(receiver)
        application = null
    }
}
