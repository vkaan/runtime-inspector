package com.vkaan.runtimeinspector.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vkaan.runtimeinspector.IInspector
import com.vkaan.runtimeinspector.RuntimeInspector

class InspectorService : Service() {

    companion object {
        const val ACTION_INSPECT = "com.vkaan.runtimeinspector.INSPECT"

        private const val TAG = "RuntimeInspector"

        // Own channel, IMPORTANCE_LOW so the ongoing notification stays silent over a payment
        // screen. RiskNotifier's channel is the loud one.
        private const val CHANNEL_ID = "runtimeinspector_service"
        private const val CHANNEL_NAME = "Runtime inspector"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, InspectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Collectors run in the host app, not here, so this side is init'd with enabled = false: it
    // owns the timeline, the rules and the notifications, and nothing else.
    private val binder = object : IInspector.Stub() {
        override fun onEvent(event: Bundle) = RuntimeInspector.record(event)

        override fun inspect() = PlatformLog.pull(this@InspectorService)
    }

    override fun onCreate() {
        super.onCreate()
        RuntimeInspector.init(
            application,
            // enabled = false: the collectors run in the host app, not here. The patterns are for
            // the platform log dump, which this side reads.
            RuntimeInspector.Config(
                enabled = false,
                cardServicePatterns = CARD_SERVICE_PATTERNS,
                cardServiceLogUnmatched = true,
            ),
        )
        PlatformLog.enableCapture(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
                )
        }
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Runtime inspector running")
                .setOngoing(true)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Lets a bench trigger the pull straight from adb, with no host app and no screen tap.
        if (intent?.action == ACTION_INSPECT) PlatformLog.pull(this, force = true)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    /**
     * The host closed — RuntimeInspector.unbind(), or its process dying. Either way the session is
     * over, so this is where the dump gets pulled and the card service rules run on it.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Host unbound — pulling the log.")
        PlatformLog.pull(this)
        // Rebind goes through onBind again, which is all a restarted host needs.
        return false
    }
}
