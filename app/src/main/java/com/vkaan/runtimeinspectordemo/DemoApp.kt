package com.vkaan.runtimeinspectordemo

import android.app.Application
import com.vkaan.runtimeinspector.RuntimeInspector

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeInspector.init(this)
    }
}