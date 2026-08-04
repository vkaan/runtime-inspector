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
import com.vkaan.runtimeinspector.collector.SystemBroadcastCollector
import com.vkaan.runtimeinspector.report.RiskNotifier
import com.vkaan.runtimeinspector.report.SessionContext
import com.vkaan.runtimeinspector.report.SessionContextFactory
import com.vkaan.runtimeinspector.rules.Risk
import com.vkaan.runtimeinspector.rules.RiskEngine
import com.vkaan.runtimeinspector.timeline.Timeline

object RuntimeInspector {

    private const val TAG = "RuntimeInspector"

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var config: Config

    private lateinit var timeline: Timeline
    private lateinit var session: SessionContext
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
            session = SessionContextFactory.create(appContext)
            val notifier =
                if (initialConfig.notifyOnRisk) RiskNotifier(appContext) else null
            timeline = Timeline(
                riskEngine = RiskEngine.withDefaultRules(
                    backStackCeiling = initialConfig.backStackCeiling,
                    heapPercentCeiling = initialConfig.heapPercentCeiling,
                    networkFlapCount = initialConfig.networkFlapCount,
                    networkFlapWindowSeconds = initialConfig.networkFlapWindowSeconds,
                    onReport = notifier?.let { { risk -> it.notify(risk) } },
                ),
            )
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
        // Printed so a lab run can be tied to a build from logcat alone, before any findings
        // have been written anywhere.
        Log.i(
            TAG,
            "Session ${session.sessionId} — ${session.appPackage} " +
                "${session.appVersionName} (${session.appVersionCode}), " +
                "lib ${session.libraryVersion}, ${session.deviceModel} API ${session.androidSdkInt}",
        )
     }


    private fun startCollectors(app: Application) {
        collectors += LifecycleCollector(timeline)
        collectors += ProcessLifecycleCollector(timeline)
        collectors += ComponentCallbacksCollector(timeline)
        collectors += ConnectivityCollector(timeline)
        collectors += CrashCollector(timeline)
        collectors += SystemBroadcastCollector(timeline)
        collectors.forEach { it.start(app) }
    }
    val isInitialized: Boolean get() = initialized

    @JvmStatic
    fun risks(): List<Risk> = if (initialized) timeline.risks() else emptyList()



    data class Config(
        val enabled: Boolean = true,
        /** Post a status-bar notification for each finding. Repeats update in place with a count. */
        val notifyOnRisk: Boolean = true,
        val backStackCeiling: Int = 10,
        val heapPercentCeiling: Int = 85,
        /** NETWORK_FLAPPING: this many losses within the window below. Must be ≤ 10 (state cap). */
        val networkFlapCount: Int = 3,
        val networkFlapWindowSeconds: Int = 60,
    )
}