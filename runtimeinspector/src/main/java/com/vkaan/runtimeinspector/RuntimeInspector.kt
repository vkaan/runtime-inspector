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
        collectors.forEach { it.start(app) }
    }
    val isInitialized: Boolean get() = initialized



    data class Config(
        val enabled: Boolean = true,
        val showOverlay: Boolean = true,
    )


}