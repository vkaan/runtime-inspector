package com.vkaan.runtimeinspector

import android.content.Context
import android.util.Log

object RuntimeInspector {

    private const val TAG = "RuntimeInspector"

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var config: Config

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