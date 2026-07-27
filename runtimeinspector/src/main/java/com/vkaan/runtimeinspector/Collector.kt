package com.vkaan.runtimeinspector

import android.app.Application

internal interface Collector {
    fun start(application: Application)
    fun stop()
}