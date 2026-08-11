package com.vkaan.runtimeinspector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.app.Application
import com.vkaan.runtimeinspector.cardservice.CardServiceLogPattern
import com.vkaan.runtimeinspector.collector.CardServiceLogCollector
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
import com.vkaan.runtimeinspector.timeline.EventSink
import com.vkaan.runtimeinspector.timeline.RemoteSink
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.Timeline

object RuntimeInspector {

    private const val TAG = "RuntimeInspector"

    const val SERVICE_PACKAGE = "com.vkaan.runtimeinspector.app"
    const val SERVICE_CLASS = "com.vkaan.runtimeinspector.app.InspectorService"
    private const val KEY_EVENT = "event"

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
                bindToService(app)
            } else {
                Log.w(TAG, "Not an Application context; lifecycle collection disabled.")
            }
        }
        Log.i(TAG, "Initialized. enabled=${config.enabled}")
        // Printed so a lab run can be tied to a build from logcat alone.
        Log.i(
            TAG,
            "Session ${session.sessionId} — ${session.appPackage} " +
                "${session.appVersionName} (${session.appVersionCode}), " +
                "lib ${session.libraryVersion}, ${session.deviceModel} API ${session.androidSdkInt}",
        )
     }

    /**
     * Collection runs here, in the host process — nothing else can see its Activities, its crash
     * handler or its heap. The findings are decided in the inspector app, across this binder.
     */
    private fun bindToService(app: Application) {
        val intent = Intent()
            .setClassName(SERVICE_PACKAGE, SERVICE_CLASS)
            // An app that has never been launched is in the stopped state and cannot be bound
            // without this.
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                startCollectors(app, RemoteSink(IInspector.Stub.asInterface(service)))
                Log.i(TAG, "Bound to the inspector service.")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "Inspector service died — collection stopped.")
                stopCollectors()
            }
        }

        if (!app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Inspector service not found — is $SERVICE_PACKAGE installed?")
        }
    }

    private fun startCollectors(app: Application, sink: EventSink) {
        synchronized(collectors) {
            if (collectors.isNotEmpty()) return
            collectors += LifecycleCollector(sink)
            collectors += ProcessLifecycleCollector(sink)
            collectors += ComponentCallbacksCollector(sink)
            collectors += ConnectivityCollector(sink)
            collectors += CrashCollector(sink)
            collectors += SystemBroadcastCollector(sink)
            addCardServiceCollector(sink)
            collectors.forEach { it.start(app) }
        }
    }

    private fun stopCollectors() {
        synchronized(collectors) {
            collectors.forEach { it.stop() }
            collectors.clear()
        }
    }

    private fun addCardServiceCollector(sink: EventSink) {
        if (!config.cardServiceEnabled) return
        if (config.cardServiceTags.isEmpty() || config.cardServicePatterns.isEmpty()) {
            Log.w(TAG, "Card service tracking is on but tags or patterns are empty — skipped.")
            return
        }
        collectors += CardServiceLogCollector(
            timeline = sink,
            tags = config.cardServiceTags,
            patterns = config.cardServicePatterns,
            logUnmatched = config.cardServiceLogUnmatched,
        )
    }
    val isInitialized: Boolean get() = initialized

    @JvmStatic
    fun risks(): List<Risk> = if (initialized) timeline.risks() else emptyList()

    /** Service side: an event that arrived over the binder from a host app. */
    @JvmStatic
    fun record(bundle: Bundle) {
        if (!initialized) return
        bundle.classLoader = RuntimeEvent::class.java.classLoader
        val event = bundle.getParcelable<RuntimeEvent>(KEY_EVENT)
        if (event == null) {
            Log.w(TAG, "Event bundle carried no event — dropped.")
            return
        }
        timeline.record(event)
    }

    data class Config(
        val enabled: Boolean = true,
        /** Post a status-bar notification for each finding. Repeats update in place with a count. */
        val notifyOnRisk: Boolean = true,
        val backStackCeiling: Int = 10,
        val heapPercentCeiling: Int = 85,
        /** NETWORK_FLAPPING: this many losses within the window below. Must be ≤ 10 (state cap). */
        val networkFlapCount: Int = 3,
        val networkFlapWindowSeconds: Int = 60,
        val cardServiceEnabled: Boolean = false,
        val cardServiceTags: List<String> = emptyList(),
        val cardServicePatterns: List<CardServiceLogPattern> = emptyList(),
        /** Log every card service line that matched no pattern. Off in the field — it prints third-party log text. */
        val cardServiceLogUnmatched: Boolean = false,
    )
}
