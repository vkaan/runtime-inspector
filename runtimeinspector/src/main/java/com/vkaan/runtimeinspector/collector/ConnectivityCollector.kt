package com.vkaan.runtimeinspector.collector

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.Timeline

internal class ConnectivityCollector(
    private val timeline: Timeline,
) : Collector {

    private var connectivityManager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            record(RuntimeEvent.Network.State.AVAILABLE, transportsOf(network))
        }

        override fun onLost(network: Network) {
            record(RuntimeEvent.Network.State.LOST)
        }
    }

    override fun start(application: Application) {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return
        connectivityManager = manager
        manager.registerDefaultNetworkCallback(callback)
    }

    override fun stop() {
        connectivityManager?.unregisterNetworkCallback(callback)
        connectivityManager = null
    }

    private fun transportsOf(network: Network): List<String> {
        val caps = connectivityManager?.getNetworkCapabilities(network) ?: return emptyList()
        return buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }
    }

    private fun record(state: RuntimeEvent.Network.State, transports: List<String> = emptyList()) {
        timeline.record { seq ->
            RuntimeEvent.Network(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                state = state,
                transports = transports,
            )
        }
    }
}
