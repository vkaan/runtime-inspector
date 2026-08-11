package com.vkaan.runtimeinspector.timeline

import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import com.vkaan.runtimeinspector.IInspector
import java.util.concurrent.atomic.AtomicLong

/**
 * The host side of the binder. Every collector in this process funnels through one sink, so the
 * sequence number is assigned here and the service keeps it.
 */
internal class RemoteSink(private val remote: IInspector) : EventSink {

    private companion object {
        const val TAG = "RuntimeInspector"
        const val KEY_EVENT = "event"
    }

    private val seq = AtomicLong()

    override fun record(build: (seq: Long) -> RuntimeEvent) {
        val bundle = Bundle().apply { putParcelable(KEY_EVENT, build(seq.getAndIncrement())) }
        try {
            remote.onEvent(bundle)
        } catch (e: RemoteException) {
            Log.w(TAG, "Inspector service is gone - event dropped: ${e.message}")
        }
    }
}
