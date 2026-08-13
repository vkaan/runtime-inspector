package com.vkaan.runtimeinspector.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vkaan.runtimeinspector.IInspector
import com.vkaan.runtimeinspector.RuntimeInspector

class InspectorService : Service() {

    companion object {
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
            ),
        )
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent): IBinder = binder
}
