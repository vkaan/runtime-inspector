package com.vkaan.runtimeinspector.collector

import android.app.Application

internal interface Collector {
    fun start(application: Application)
    fun stop()
}