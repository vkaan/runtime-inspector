package com.vkaan.runtimeinspector

import android.content.Context
import android.util.Log
import android.app.Application

object RuntimeInspector {

    private const val TAG = "RuntimeInspector"

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var config: Config

    private val timeline = Timeline()
    private val collectors = mutableListOf<Collector>()

    @JvmStatic
    @JvmOverloads
    fun init(context: Context, config: Config = Config()) {
        if (initialized) {
            Log.w(TAG, "init() called more than once — ignoring.")
            return
        }
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            this.config = config
            initialized = true
        }
        if (config.enabled) {
            val app = appContext as? Application
            if (app != null) {
                startCollectors(app)
            } else {
                Log.w(TAG, "Not an Application context; lifecycle collection disabled.")
            }
        }
        Log.i(TAG, "Initialized. enabled=${config.enabled}")
    }


    private fun startCollectors(app: Application) {
        collectors += LifecycleCollector(timeline)
        collectors += ProcessLifecycleCollector(timeline)
        collectors += ComponentCallbacksCollector(timeline)
        collectors.forEach { it.start(app) }
    }
    val isInitialized: Boolean get() = initialized



    data class Config(
        val enabled: Boolean = true,
        val showOverlay: Boolean = true,
    )

    @JvmStatic
    @JvmOverloads
    fun dump(lastN: Int = 20): String {
        if (!initialized) return "RuntimeInspector not initialized."

        val state = timeline.state()
        val events = timeline.snapshot().takeLast(lastN)

        val text = buildString {
            appendLine("=== RuntimeState ===")
            appendLine("appInForeground  = ${state.appInForeground}")
            appendLine("foregroundScreen = ${state.foregroundScreen ?: "-"}")
            appendLine("backStackDepth   = ${state.backStackDepth}")
            appendLine("lastTrimMemory   = ${state.lastTrimMemory ?: "-"}")
            appendLine("lastConfigChange = ${state.lastConfigChange.joinToString("|").ifEmpty { "-" }}")
            appendLine("lastSeq          = ${state.lastSeq}")
            appendLine()
            appendLine("=== Timeline (last ${events.size}) ===")
            events.forEach { appendLine("#${it.seq}  ${it.logLine()}") }
        }

        Log.d(TAG, text)
        return text
    }


}