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
    private var collector: LifecycleCollector? = null

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
                collector = LifecycleCollector().also { it.start(app) }
            } else {
                Log.w(TAG, "Not an Application context; lifecycle collection disabled.")
            }
        }
        Log.i(TAG, "Initialized. enabled=${config.enabled}")
    }

    val isInitialized: Boolean get() = initialized

    internal fun context(): Context =
        if (initialized) appContext
        else error("RuntimeInspector.init() must be called before use.")

    data class Config(
        val enabled: Boolean = true,
        val showOverlay: Boolean = true,
    )


}