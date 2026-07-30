package com.vkaan.runtimeinspector

import android.content.Context
import android.util.Log
import android.app.Application
import com.vkaan.runtimeinspector.collector.Collector
import com.vkaan.runtimeinspector.collector.ComponentCallbacksCollector
import com.vkaan.runtimeinspector.collector.ConnectivityCollector
import com.vkaan.runtimeinspector.collector.CrashCollector
import com.vkaan.runtimeinspector.collector.LifecycleCollector
import com.vkaan.runtimeinspector.collector.ProcessLifecycleCollector
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.timeline.Timeline

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
    fun init(context: Context, initialConfig: Config = Config()) {
        if (initialized) {
            Log.w(TAG, "init() called more than once — ignoring.")
            return
        }
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            config = initialConfig
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
        collectors += ConnectivityCollector(timeline)
        collectors += CrashCollector(timeline)
        collectors.forEach { it.start(app) }
    }
    val isInitialized: Boolean get() = initialized

    @JvmStatic
    fun risks(): List<Risk> = timeline.risks()



    data class Config(
        val enabled: Boolean = true,
        val showOverlay: Boolean = true,
    )
}