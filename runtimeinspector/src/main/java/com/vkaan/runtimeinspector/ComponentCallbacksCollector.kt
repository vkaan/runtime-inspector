package com.vkaan.runtimeinspector

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock

internal class ComponentCallbacksCollector(
    private val timeline: Timeline,
) : Collector, ComponentCallbacks2 {

    private val lock = Any()
    private var application: Application? = null
    private var lastConfiguration: Configuration? = null

    override fun start(application: Application) {
        this.application = application
        synchronized(lock) {
            lastConfiguration = Configuration(application.resources.configuration)
        }
        application.registerComponentCallbacks(this)
    }

    override fun stop() {
        application?.unregisterComponentCallbacks(this)
        application = null
        synchronized(lock) { lastConfiguration = null }
    }

    override fun onTrimMemory(level: Int) = recordMemory(level)
    override fun onLowMemory() = recordMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onConfigurationChanged(newConfig: Configuration) {
        val changed: List<String>
        synchronized(lock) {
            val previous = lastConfiguration
            changed = if (previous != null) decodeDiff(previous.diff(newConfig)) else emptyList()
            lastConfiguration = Configuration(newConfig)
        }
        timeline.record { seq ->
            RuntimeEvent.ConfigChange(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                changedFields = changed,
            )
        }
    }

    private fun recordMemory(level: Int) {
        timeline.record { seq ->
            RuntimeEvent.Memory(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                level = level,
            )
        }
    }

    private fun decodeDiff(diff: Int): List<String> = buildList {
        if ((diff and ActivityInfo.CONFIG_ORIENTATION) != 0) add("ORIENTATION")
        if ((diff and ActivityInfo.CONFIG_SCREEN_SIZE) != 0) add("SCREEN_SIZE")
        if ((diff and ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE) != 0) add("SMALLEST_SCREEN_SIZE")
        if ((diff and ActivityInfo.CONFIG_UI_MODE) != 0) add("UI_MODE")
        if ((diff and ActivityInfo.CONFIG_LOCALE) != 0) add("LOCALE")
        if ((diff and ActivityInfo.CONFIG_FONT_SCALE) != 0) add("FONT_SCALE")
        if ((diff and ActivityInfo.CONFIG_DENSITY) != 0) add("DENSITY")
        if ((diff and ActivityInfo.CONFIG_KEYBOARD_HIDDEN) != 0) add("KEYBOARD_HIDDEN")
        if ((diff and ActivityInfo.CONFIG_LAYOUT_DIRECTION) != 0) add("LAYOUT_DIRECTION")
    }

}