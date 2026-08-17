package com.vkaan.runtimeinspector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.app.Application
import com.vkaan.runtimeinspector.cardservice.CardServiceLogPattern
import com.vkaan.runtimeinspector.cardservice.CardServiceLogState
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

    const val SERVICE_PACKAGE = "com.vkaan.runtimeinspector"
    const val SERVICE_CLASS = "com.vkaan.runtimeinspector.app.InspectorService"
    private const val KEY_EVENT = "event"
    private const val UNMATCHED_LOG_LIMIT = 50

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var config: Config

    private lateinit var timeline: Timeline
    private lateinit var session: SessionContext
    private val collectors = mutableListOf<Collector>()

    @Volatile
    private var remote: IInspector? = null

    /** Held so unbind() can release the binding the host opened in init(). */
    @Volatile
    private var connection: ServiceConnection? = null

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
                val inspector = IInspector.Stub.asInterface(service)
                remote = inspector
                startCollectors(app, RemoteSink(inspector))
                Log.i(TAG, "Bound to the inspector service.")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "Inspector service died — collection stopped.")
                remote = null
                stopCollectors()
            }
        }

        if (app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            this.connection = connection
        } else {
            Log.e(TAG, "Inspector service not found — is $SERVICE_PACKAGE installed?")
        }
    }

    /**
     * The counterpart to init(): the host calls this as it closes. Unbinding is what tells the
     * inspector app the session is over, and that is where the log is pulled and the rules run.
     */
    @JvmStatic
    fun unbind() {
        val bound = synchronized(this) {
            // Lets init() run again if the host reopens in the same process — that is a new session.
            initialized = false
            connection?.also { connection = null }
        } ?: return
        stopCollectors()
        remote = null
        try {
            appContext.unbindService(bound)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Already unbound: ${e.message}")
        }
        Log.i(TAG, "Unbound from the inspector service.")
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

    /** Ask the inspector app to pull the platform log dump and run the card service rules on it. */
    @JvmStatic
    fun inspect() {
        val inspector = remote
        if (inspector == null) {
            Log.w(TAG, "inspect() called before the inspector service connected — ignored.")
            return
        }
        try {
            inspector.inspect()
        } catch (e: RemoteException) {
            Log.w(TAG, "Inspector service is gone — inspect() dropped: ${e.message}")
        }
    }

    /**
     * Service side: card service lines pulled from the platform log dump. Same tracker the logcat
     * reader drives, one line at a time, in file order.
     */
    @JvmStatic
    fun readCardServiceLines(lines: Sequence<String>): Int {
        if (!initialized) return 0
        if (config.cardServicePatterns.isEmpty()) {
            Log.w(TAG, "No card service patterns configured — dump not read.")
            return 0
        }
        Log.i(TAG, "Card service dump read — start.")
        val tracker = CardServiceLogState(config.cardServicePatterns)
        var matches = 0
        var read = 0L
        var unmatchedLogged = 0
        for (line in lines) {
            read++
            val match = tracker.onLine(line, SystemClock.elapsedRealtimeNanos())
            if (match == null) {
                // Capped: a dump can run to thousands of lines and logcat's buffer is not big.
                if (config.cardServiceLogUnmatched && unmatchedLogged < UNMATCHED_LOG_LIMIT) {
                    unmatchedLogged++
                    Log.d(TAG, "Card service line unmatched: $line")
                }
                continue
            }
            matches++
            timeline.record { seq ->
                RuntimeEvent.CardService(
                    seq = seq,
                    timestampMillis = System.currentTimeMillis(),
                    elapsedRealtimeNanos = match.elapsedRealtimeNanos,
                    from = match.from,
                    to = match.to,
                    apis = match.apis,
                    line = match.line,
                )
            }
        }
        Log.i(TAG, "Card service dump read — done, $read lines, $matches matched.")
        return matches
    }

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
