package com.vkaan.runtimeinspector.timeline

/** Where a collector hands its events. Timeline in the service, RemoteSink in the host app. */
internal interface EventSink {
    fun record(build: (seq: Long) -> RuntimeEvent)
}
